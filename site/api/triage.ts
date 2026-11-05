/**
 * PropCare AI photo check: POST /api/triage (Vercel function, Node runtime).
 *
 * Request:  JSON { imageBase64, mimeType: image/jpeg|png|webp, description (<= 500 chars) }
 *           Optional header X-Client-Id (a public id, used only for rate limiting).
 * Response: 200 { triage } or 4xx/5xx { error } with a generic, readable message.
 *
 * Security: POST only; 1.5 MB image cap; MIME whitelist; per-IP rate limit (10 per 10 min,
 * best effort, in memory); CORS only for the site's own origin (the Android app sends no
 * Origin); the Gemini key stays in env GEMINI_API_KEY and travels in the x-goog-api-key
 * header; images and descriptions are never logged. The safety rules are applied here and
 * again in the app: words in the description always override the model.
 *
 * Self-contained on purpose (no local imports), so the same file runs in Vercel and in the tests.
 */

export const MODEL_DEFAULT = 'gemini-2.5-flash-lite';
export const MAX_IMAGE_BYTES = 1.5 * 1024 * 1024;
export const MAX_DESCRIPTION = 500;
const MAX_BODY_CHARS = Math.ceil((MAX_IMAGE_BYTES * 4) / 3) + 4096;
const RATE_LIMIT = 10;
const RATE_WINDOW_MS = 10 * 60 * 1000;
const MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export const TRADES = ['plumber', 'electrician', 'gas_engineer', 'heating', 'roofer', 'carpenter', 'decorator', 'plasterer', 'flooring', 'locksmith', 'damp', 'general'] as const;
export const URGENCIES = ['emergency', 'urgent', 'routine'] as const;
export const SAFETY_FLAGS = ['gas_smell', 'carbon_monoxide', 'electrical_hazard', 'water_near_electrics', 'structural', 'fire_risk'] as const;
const CONFIDENCE = ['low', 'medium', 'high'] as const;

type Trade = (typeof TRADES)[number];
type Urgency = (typeof URGENCIES)[number];
type SafetyFlag = (typeof SAFETY_FLAGS)[number];

/** Matches the Android app's domain/Models.kt Triage. */
export interface Triage {
  isPropertyIssue: boolean;
  issueSummary: string;
  likelyCauses: string[];
  trade: Trade;
  urgency: Urgency;
  safetyFlags: SafetyFlag[];
  cannotTell: string[];
  customerAdvice: string[];
  confidence: (typeof CONFIDENCE)[number];
  source: 'ai';
  model: string;
  /** [ymin, xmin, ymax, xmax] on a 0-1000 scale, or null when there is no single area. */
  box: [number, number, number, number] | null;
  labels: string[];
}

export const SYSTEM_PROMPT = `You help a UK property maintenance company triage repair requests from customers.
You see one photo and the customer's short description. Reply only with JSON matching the schema.

Rules:
- Describe what is visible. Never claim a definite diagnosis; a technician confirms on site.
- issue_summary: one plain sentence, under 120 characters.
- likely_causes: up to 3 short possibilities, most likely first.
- trade: the single trade best placed to attend.
- urgency: "emergency" = danger to people or rapid damage now (gas smell, sparking, flooding, no heating for a vulnerable person, unsafe structure, cannot secure the home). "urgent" = should be seen within a few days. "routine" = can be booked normally.
- safety_flags: only flags that apply; empty if none.
- cannot_tell: up to 3 things the photo cannot show that matter (for example hidden pipework, wiring behind walls, the source of a leak).
- customer_advice: up to 3 safe, simple steps the customer can take now. Never suggest electrical, gas or structural work by the customer.
- box_2d: the box around the main visible problem as [ymin, xmin, ymax, xmax], each 0 to 1000 relative to the image. An empty array if there is no single area.
- is_property_issue: false if the photo does not show a property problem.
- Use British English.`;

const str = { type: 'STRING' };
const strList = (max: number) => ({ type: 'ARRAY', items: str, maxItems: String(max) });

export const RESPONSE_SCHEMA = {
  type: 'OBJECT',
  properties: {
    is_property_issue: { type: 'BOOLEAN' },
    issue_summary: str,
    likely_causes: strList(3),
    trade: { type: 'STRING', enum: [...TRADES] },
    urgency: { type: 'STRING', enum: [...URGENCIES] },
    safety_flags: { type: 'ARRAY', items: { type: 'STRING', enum: [...SAFETY_FLAGS] } },
    cannot_tell: strList(3),
    customer_advice: strList(3),
    confidence: { type: 'STRING', enum: [...CONFIDENCE] },
    box_2d: { type: 'ARRAY', items: { type: 'INTEGER' }, maxItems: '4' },
  },
  required: ['is_property_issue', 'issue_summary', 'likely_causes', 'trade', 'urgency', 'safety_flags', 'cannot_tell', 'customer_advice', 'confidence', 'box_2d'],
};

/** A deliberately small request: one image, short output, no thinking budget. */
export function buildGeminiRequest(imageBase64: string, mimeType: string, description: string) {
  return {
    systemInstruction: { parts: [{ text: SYSTEM_PROMPT }] },
    contents: [
      {
        role: 'user',
        parts: [{ inlineData: { mimeType, data: imageBase64 } }, { text: `Customer description: ${clip(description, MAX_DESCRIPTION) || '(none given)'}` }],
      },
    ],
    generationConfig: {
      temperature: 0.2,
      maxOutputTokens: 600,
      responseMimeType: 'application/json',
      responseSchema: RESPONSE_SCHEMA,
      thinkingConfig: { thinkingBudget: 0 },
    },
  };
}

/** An error whose message is safe to show to the user. */
class PublicError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

/** Pulls the JSON object out of a Gemini generateContent response. */
export function extractGeminiJson(response: any): Record<string, unknown> {
  if (response?.promptFeedback?.blockReason) throw new PublicError(422, 'This photo could not be checked. Try a different photo.');
  const text: string | undefined = response?.candidates?.[0]?.content?.parts?.find((p: any) => typeof p?.text === 'string')?.text;
  if (!text) throw new PublicError(502, 'The photo check returned no answer. Try again.');
  let data: unknown;
  try {
    data = JSON.parse(text);
  } catch {
    const match = text.match(/\{[\s\S]*\}/);
    if (!match) throw new PublicError(502, 'The photo check answer could not be read. Try again.');
    try {
      data = JSON.parse(match[0]);
    } catch {
      throw new PublicError(502, 'The photo check answer could not be read. Try again.');
    }
  }
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw new PublicError(502, 'The photo check answer could not be read. Try again.');
  return data as Record<string, unknown>;
}

// Safety rules: the same patterns as the app's domain/Safety.kt.
const GAS = /\b(smell(s|ing)? (of )?gas|gas smell|gas leak|smells? like gas|rotten eggs?)\b/i;
const CO = /\b(carbon monoxide|co alarm|co detector)\b/i;
const SPARK = /\b(spark(s|ing)?|burning smell|smoke from (the )?(socket|switch|plug)|scorch(ed)?|electric shock|melt(ed|ing) (socket|plug|wire))\b/i;
const WATER_ELEC = /\b(water|leak(ing)?|drip(ping)?)\b[\s\S]{0,40}\b(socket|light|fuse ?box|consumer unit|wiring|switch)\b|\b(socket|light|fuse ?box|consumer unit|wiring|switch)\b[\s\S]{0,40}\b(water|leak(ing)?|drip(ping)?|wet)\b/i;
const FLOOD = /\b(flood(ing|ed)?|burst pipe|water pouring|gushing)\b/i;
const STRUCTURAL = /\b(ceiling (is )?(collaps\w*|sagging|bulging)|wall (leaning|bowing|collaps\w*))\b/i;
const FIRE = /\b(fire|flames?)\b/i;

export function safetyFromText(text: string): SafetyFlag[] {
  const flags: SafetyFlag[] = [];
  if (GAS.test(text)) flags.push('gas_smell');
  if (CO.test(text)) flags.push('carbon_monoxide');
  if (SPARK.test(text)) flags.push('electrical_hazard');
  if (WATER_ELEC.test(text)) flags.push('water_near_electrics');
  if (STRUCTURAL.test(text)) flags.push('structural');
  if (FIRE.test(text)) flags.push('fire_risk');
  return flags;
}

function validBox(v: unknown): Triage['box'] {
  if (!Array.isArray(v) || v.length !== 4) return null;
  const b = v.map((n) => Math.round(Number(n)));
  if (b.some((n) => !Number.isFinite(n) || n < 0 || n > 1000)) return null;
  if (b[2] <= b[0] || b[3] <= b[1]) return null;
  return [b[0], b[1], b[2], b[3]];
}

/** Validates and cleans the model's output, then applies the safety rules. */
export function normaliseTriage(raw: Record<string, unknown>, description: string, model: string): Triage {
  const pick = <T extends string>(v: unknown, allowed: readonly T[], fallback: T): T =>
    typeof v === 'string' && (allowed as readonly string[]).includes(v) ? (v as T) : fallback;
  const list = (v: unknown, max: number) =>
    Array.isArray(v) ? v.filter((x) => typeof x === 'string' && x.trim()).map((x) => clip(String(x).trim(), 160)).slice(0, max) : [];

  const modelFlags = Array.isArray(raw.safety_flags) ? (raw.safety_flags.filter((f) => (SAFETY_FLAGS as readonly string[]).includes(String(f))) as SafetyFlag[]) : [];
  const safetyFlags = [...new Set([...modelFlags, ...safetyFromText(description)])];

  let trade = pick(raw.trade, TRADES, 'general');
  let urgency = pick(raw.urgency, URGENCIES, 'routine');
  if (FLOOD.test(description) && urgency === 'routine') urgency = 'urgent';
  if (safetyFlags.length > 0) urgency = 'emergency';
  if (safetyFlags.includes('gas_smell') || safetyFlags.includes('carbon_monoxide')) trade = 'gas_engineer';
  else if (safetyFlags.includes('electrical_hazard') || safetyFlags.includes('water_near_electrics')) trade = 'electrician';

  return {
    isPropertyIssue: raw.is_property_issue !== false,
    issueSummary: clip(typeof raw.issue_summary === 'string' && raw.issue_summary.trim() ? raw.issue_summary.trim() : 'Issue reported by the customer', 160),
    likelyCauses: list(raw.likely_causes, 3),
    trade,
    urgency,
    safetyFlags,
    cannotTell: list(raw.cannot_tell, 3),
    customerAdvice: list(raw.customer_advice, 3),
    confidence: pick(raw.confidence, CONFIDENCE, 'low'),
    source: 'ai',
    model,
    box: validBox(raw.box_2d ?? raw.box),
    labels: [],
  };
}

function clip(s: string, max: number): string {
  return s.length > max ? s.slice(0, max - 1).trimEnd() + '…' : s;
}

// Rate limiting: best effort, per warm instance. Keyed by IP (falls back to the client id).
const hits = new Map<string, number[]>();
export function rateLimited(key: string, now = Date.now()): boolean {
  const recent = (hits.get(key) ?? []).filter((t) => now - t < RATE_WINDOW_MS);
  if (recent.length >= RATE_LIMIT) {
    hits.set(key, recent);
    return true;
  }
  recent.push(now);
  hits.set(key, recent);
  if (hits.size > 5000) for (const [k, v] of hits) if (!v.some((t) => now - t < RATE_WINDOW_MS)) hits.delete(k);
  return false;
}
export function resetRateLimit() {
  hits.clear();
}

function allowedOrigins(): string[] {
  return (process.env.SITE_ORIGIN ?? '').split(',').map((s) => s.trim().replace(/\/$/, '')).filter(Boolean);
}

/** Same-origin browser calls, configured site origins and Vercel previews of this project are allowed. */
function originAllowed(origin: string, requestUrl: string): boolean {
  try {
    const o = new URL(origin);
    if (o.origin === new URL(requestUrl).origin) return true;
    return allowedOrigins().includes(o.origin);
  } catch {
    return false;
  }
}

function json(status: number, body: unknown, origin?: string | null): Response {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  };
  if (origin) {
    headers['Access-Control-Allow-Origin'] = origin;
    headers['Vary'] = 'Origin';
  }
  return new Response(JSON.stringify(body), { status, headers });
}

function clientKey(req: Request): string {
  const fwd = req.headers.get('x-forwarded-for')?.split(',')[0]?.trim();
  return fwd || req.headers.get('x-real-ip') || `client:${(req.headers.get('x-client-id') ?? 'unknown').slice(0, 64)}`;
}

export function OPTIONS(req: Request): Response {
  const origin = req.headers.get('origin');
  if (!origin || !originAllowed(origin, req.url)) return new Response(null, { status: 403 });
  return new Response(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': origin,
      'Access-Control-Allow-Methods': 'POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, X-Client-Id',
      'Access-Control-Max-Age': '600',
      Vary: 'Origin',
    },
  });
}

export async function POST(req: Request): Promise<Response> {
  const origin = req.headers.get('origin');
  // Browsers send Origin; only our site may call. The Android app sends none.
  if (origin && !originAllowed(origin, req.url)) return json(403, { error: 'This site is not allowed to use the photo check.' });
  const cors = origin ?? null;

  if (rateLimited(clientKey(req))) return json(429, { error: 'Too many checks. Wait a few minutes and try again.' }, cors);

  const declared = Number(req.headers.get('content-length') ?? '0');
  if (declared > MAX_BODY_CHARS) return json(413, { error: 'The photo is too large. Use one under 1.5 MB.' }, cors);
  if (!(req.headers.get('content-type') ?? '').toLowerCase().includes('application/json')) return json(415, { error: 'Send the photo as JSON.' }, cors);

  let text: string;
  try {
    text = await req.text();
  } catch {
    return json(400, { error: 'The request could not be read.' }, cors);
  }
  if (text.length > MAX_BODY_CHARS) return json(413, { error: 'The photo is too large. Use one under 1.5 MB.' }, cors);

  let body: any;
  try {
    body = JSON.parse(text);
  } catch {
    return json(400, { error: 'The request could not be read.' }, cors);
  }
  const imageBase64 = typeof body?.imageBase64 === 'string' ? body.imageBase64.replace(/^data:[^,]*,/, '') : '';
  const mimeType = typeof body?.mimeType === 'string' ? body.mimeType.toLowerCase() : '';
  const description = typeof body?.description === 'string' ? body.description : '';

  if (!MIME_TYPES.includes(mimeType)) return json(415, { error: 'Use a JPEG, PNG or WebP photo.' }, cors);
  if (!imageBase64 || imageBase64.length % 4 === 1 || !/^[A-Za-z0-9+/]+={0,2}$/.test(imageBase64)) return json(400, { error: 'Add a photo to check.' }, cors);
  const bytes = Math.floor((imageBase64.length * 3) / 4) - (imageBase64.endsWith('==') ? 2 : imageBase64.endsWith('=') ? 1 : 0);
  if (bytes > MAX_IMAGE_BYTES) return json(413, { error: 'The photo is too large. Use one under 1.5 MB.' }, cors);
  if (description.length > MAX_DESCRIPTION) return json(400, { error: 'Keep the description under 500 characters.' }, cors);

  const key = process.env.GEMINI_API_KEY;
  if (!key) return json(503, { error: 'The photo check is not available right now.' }, cors);
  const model = (process.env.GEMINI_MODEL || MODEL_DEFAULT).replace(/[^a-z0-9.\-]/gi, '');

  try {
    const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
      body: JSON.stringify(buildGeminiRequest(imageBase64, mimeType, description)),
      signal: AbortSignal.timeout(25_000),
    });
    if (!res.ok) {
      // Log the status only: never the image, the description or the key.
      console.error(`triage: upstream status ${res.status}`);
      return json(res.status === 429 ? 503 : 502, { error: 'The photo check is busy. Try again in a minute.' }, cors);
    }
    const raw = extractGeminiJson(await res.json());
    return json(200, { triage: normaliseTriage(raw, description, model) }, cors);
  } catch (e) {
    if (e instanceof PublicError) return json(e.status, { error: e.message }, cors);
    console.error(`triage: ${e instanceof Error ? e.name : 'error'}`);
    return json(502, { error: 'The photo check did not respond. Try again.' }, cors);
  }
}
