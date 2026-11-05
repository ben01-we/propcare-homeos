// API tests with a mocked Gemini response. Run: npm test (Node 22.6+, no network, no key needed).
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { POST, OPTIONS, resetRateLimit, normaliseTriage, buildGeminiRequest } from '../api/triage.ts';

const URL_ = 'https://propcare.example/api/triage';
const IMG = Buffer.from('fake-jpeg-bytes').toString('base64');
const FAKE_KEY = 'test-key-not-real';

type Call = { url: string; init: RequestInit };
let calls: Call[] = [];

function mockGemini(payload: unknown, status = 200) {
  calls = [];
  globalThis.fetch = (async (url: string, init: RequestInit) => {
    calls.push({ url: String(url), init });
    return new Response(JSON.stringify(payload), { status, headers: { 'Content-Type': 'application/json' } });
  }) as typeof fetch;
}

const modelAnswer = (o: Record<string, unknown>) => ({ candidates: [{ content: { parts: [{ text: JSON.stringify(o) }] } }] });
const leak = {
  is_property_issue: true, issue_summary: 'Water stain spreading across the kitchen ceiling.', likely_causes: ['Leaking pipe above', 'Bath seal', 'Roof', 'Extra'],
  trade: 'plumber', urgency: 'urgent', safety_flags: [], cannot_tell: ['Where the water comes from'], customer_advice: ['Put a bucket under it.'],
  confidence: 'medium', box_2d: [120, 300, 560, 780],
};

function req(body: unknown, headers: Record<string, string> = {}, method = 'POST') {
  const text = typeof body === 'string' ? body : JSON.stringify(body);
  return new Request(URL_, { method, body: method === 'POST' ? text : undefined, headers: { 'Content-Type': 'application/json', 'x-forwarded-for': `10.0.0.${Math.floor(Math.random() * 250)}`, ...headers } });
}

beforeEach(() => {
  resetRateLimit();
  process.env.GEMINI_API_KEY = FAKE_KEY;
  delete process.env.GEMINI_MODEL;
  process.env.SITE_ORIGIN = 'https://propcare-homeos.vercel.app';
  mockGemini(modelAnswer(leak));
});

test('valid photo returns the triage with box, in the app contract shape', async () => {
  const res = await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg', description: 'Stain on the ceiling' }));
  assert.equal(res.status, 200);
  const { triage } = await res.json();
  assert.equal(triage.issueSummary, leak.issue_summary);
  assert.deepEqual(triage.box, [120, 300, 560, 780]);
  assert.equal(triage.likelyCauses.length, 3);
  assert.equal(triage.trade, 'plumber');
  assert.equal(triage.urgency, 'urgent');
  assert.equal(triage.source, 'ai');
  assert.equal(triage.model, 'gemini-2.5-flash-lite');
  assert.deepEqual(Object.keys(triage).sort(), ['box', 'cannotTell', 'confidence', 'customerAdvice', 'isPropertyIssue', 'issueSummary', 'labels', 'likelyCauses', 'model', 'safetyFlags', 'source', 'trade', 'urgency']);
});

test('key goes in the x-goog-api-key header, never the URL; thinking off; 600 tokens', async () => {
  await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg', description: '' }));
  assert.equal(calls.length, 1);
  assert.ok(!calls[0].url.includes(FAKE_KEY));
  assert.ok(calls[0].url.includes('models/gemini-2.5-flash-lite:generateContent'));
  assert.equal((calls[0].init.headers as Record<string, string>)['x-goog-api-key'], FAKE_KEY);
  const sent = JSON.parse(String(calls[0].init.body));
  assert.equal(sent.generationConfig.maxOutputTokens, 600);
  assert.equal(sent.generationConfig.thinkingConfig.thinkingBudget, 0);
  assert.ok(sent.generationConfig.responseSchema.properties.box_2d);
});

test('GEMINI_MODEL overrides the model', async () => {
  process.env.GEMINI_MODEL = 'gemini-2.5-flash';
  await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg' }));
  assert.ok(calls[0].url.includes('models/gemini-2.5-flash:generateContent'));
});

test('safety words in the description override the model', async () => {
  const res = await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg', description: 'There is a smell of gas near the boiler' }));
  const { triage } = await res.json();
  assert.equal(triage.urgency, 'emergency');
  assert.equal(triage.trade, 'gas_engineer');
  assert.ok(triage.safetyFlags.includes('gas_smell'));
});

test('sparking socket is an emergency for an electrician', async () => {
  const { triage } = await (await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg', description: 'the socket is sparking' }))).json();
  assert.equal(triage.urgency, 'emergency');
  assert.equal(triage.trade, 'electrician');
});

test('invalid box from the model becomes null', () => {
  assert.equal(normaliseTriage({ ...leak, box_2d: [500, 500, 100, 900] }, '', 'm').box, null);
  assert.equal(normaliseTriage({ ...leak, box_2d: [0, 0, 1200, 10] }, '', 'm').box, null);
  assert.equal(normaliseTriage({ ...leak, box_2d: [] }, '', 'm').box, null);
});

test('rejects other methods by not exporting them, and bad input with clear errors', async () => {
  assert.equal((await POST(req({ imageBase64: IMG, mimeType: 'image/gif' }))).status, 415);
  assert.equal((await POST(req({ imageBase64: '', mimeType: 'image/jpeg' }))).status, 400);
  assert.equal((await POST(req({ imageBase64: 'not base64!!', mimeType: 'image/jpeg' }))).status, 400);
  assert.equal((await POST(req('{not json'))).status, 400);
  assert.equal((await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg', description: 'x'.repeat(501) }))).status, 400);
  const big = 'A'.repeat(Math.ceil((1.6 * 1024 * 1024 * 4) / 3 / 4) * 4);
  assert.equal((await POST(req({ imageBase64: big, mimeType: 'image/jpeg' }))).status, 413);
  assert.equal(calls.length, 0, 'Gemini must not be called for rejected requests');
});

test('rate limit: the 11th request in 10 minutes from one IP gets 429', async () => {
  const h = { 'x-forwarded-for': '203.0.113.9' };
  for (let i = 0; i < 10; i++) assert.equal((await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg' }, h))).status, 200);
  assert.equal((await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg' }, h))).status, 429);
});

test('CORS: own site allowed, other origins refused, no Origin (Android app) allowed', async () => {
  const ok = await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg' }, { origin: 'https://propcare-homeos.vercel.app' }));
  assert.equal(ok.status, 200);
  assert.equal(ok.headers.get('access-control-allow-origin'), 'https://propcare-homeos.vercel.app');
  const same = await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg' }, { origin: 'https://propcare.example' }));
  assert.equal(same.status, 200);
  const evil = await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg' }, { origin: 'https://evil.example' }));
  assert.equal(evil.status, 403);
  assert.equal(evil.headers.get('access-control-allow-origin'), null);
  const app = await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg' }));
  assert.equal(app.status, 200);
  assert.equal(app.headers.get('access-control-allow-origin'), null);
  assert.equal(OPTIONS(new Request(URL_, { method: 'OPTIONS', headers: { origin: 'https://evil.example' } })).status, 403);
  assert.equal(OPTIONS(new Request(URL_, { method: 'OPTIONS', headers: { origin: 'https://propcare-homeos.vercel.app' } })).status, 204);
});

test('upstream failures give generic errors and never echo the key or upstream text', async () => {
  mockGemini({ error: { message: `API key ${FAKE_KEY} invalid` } }, 400);
  const res = await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg' }));
  assert.equal(res.status, 502);
  const text = await res.text();
  assert.ok(!text.includes(FAKE_KEY) && !text.includes('invalid'));
  mockGemini({ promptFeedback: { blockReason: 'SAFETY' } });
  assert.equal((await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg' }))).status, 422);
  mockGemini({ candidates: [{ content: { parts: [{ text: 'not json at all' }] } }] });
  assert.equal((await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg' }))).status, 502);
});

test('missing key gives 503 without calling Gemini', async () => {
  delete process.env.GEMINI_API_KEY;
  const res = await POST(req({ imageBase64: IMG, mimeType: 'image/jpeg' }));
  assert.equal(res.status, 503);
  assert.equal(calls.length, 0);
});

test('description is clipped in the prompt', () => {
  const r = buildGeminiRequest(IMG, 'image/png', 'y'.repeat(800));
  assert.ok(r.contents[0].parts[1].text!.length < 540);
});
