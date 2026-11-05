// PropCare HomeOS landing page: the try-it-now photo check.
// The photo is shrunk to 768px in the browser, sent to /api/triage, and the result is drawn
// with the AI's box ([ymin, xmin, ymax, xmax], 0-1000) over the photo.

// Set these when the files exist (the APK from the release build, the demo video for the submission).
const APK_URL = 'downloads/PropCare-HomeOS.apk';
const DEMO_VIDEO_URL = '';

const TRADES = {
  plumber: 'Plumber', electrician: 'Electrician', gas_engineer: 'Gas Safe engineer', heating: 'Heating engineer',
  roofer: 'Roofer', carpenter: 'Carpenter', decorator: 'Decorator', plasterer: 'Plasterer', flooring: 'Flooring fitter',
  locksmith: 'Locksmith', damp: 'Damp specialist', general: 'General maintenance',
};
const URGENCY = { emergency: 'Emergency', urgent: 'Within a few days', routine: 'Routine' };
// Example price bands in pounds, the same as the app's Company.kt. Always labelled as examples.
const PRICE = {
  plumber: [80, 180], electrician: [90, 200], gas_engineer: [70, 150], heating: [90, 250], roofer: [150, 450], carpenter: [80, 220],
  decorator: [150, 600], plasterer: [120, 400], flooring: [200, 800], locksmith: [70, 140], damp: [150, 500], general: [60, 150],
};

const $ = (id) => document.getElementById(id);
let photoUrl = null;
let photoBlob = null;

document.querySelectorAll('[data-apk]').forEach((a) => a.setAttribute('href', APK_URL));

if (DEMO_VIDEO_URL) {
  const v = document.createElement('video');
  v.controls = true;
  v.preload = 'none';
  v.src = DEMO_VIDEO_URL;
  $('video-slot').append(v);
  $('demo').hidden = false;
}

function showError(text) {
  const e = $('form-error');
  e.textContent = text;
  e.hidden = !text;
}

$('photo').addEventListener('change', async (ev) => {
  showError('');
  const file = ev.target.files && ev.target.files[0];
  if (!file) return;
  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
    showError('Use a JPEG, PNG or WebP photo.');
    return;
  }
  try {
    photoBlob = await shrink(file, 768);
    $('file-label').textContent = file.name;
  } catch {
    photoBlob = null;
    showError('That photo could not be opened. Try another one.');
  }
});

async function shrink(file, maxSide) {
  const bmp = await createImageBitmap(file);
  const scale = Math.min(1, maxSide / Math.max(bmp.width, bmp.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(bmp.width * scale));
  canvas.height = Math.max(1, Math.round(bmp.height * scale));
  canvas.getContext('2d').drawImage(bmp, 0, 0, canvas.width, canvas.height);
  bmp.close?.();
  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('encode'))), 'image/jpeg', 0.8));
}

function toBase64(blob) {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result).split(',')[1]);
    r.onerror = reject;
    r.readAsDataURL(blob);
  });
}

function setLoading(on) {
  const btn = $('check-btn');
  btn.disabled = on;
  btn.textContent = on ? 'Checking your photo' : 'Check photo';
  let l = $('result-loading');
  if (on && !l) {
    l = document.createElement('div');
    l.id = 'result-loading';
    l.className = 'loading';
    l.innerHTML = '<div class="spinner" aria-hidden="true"></div><p>Checking your photo. This takes a few seconds.</p>';
    $('result').prepend(l);
  }
  if (l) l.hidden = !on;
  if (on) { $('result-empty').hidden = true; $('result-card').hidden = true; }
}

$('check-form').addEventListener('submit', async (ev) => {
  ev.preventDefault();
  showError('');
  if (!photoBlob) {
    showError('Choose a photo first.');
    $('photo').focus();
    return;
  }
  const description = $('desc').value.trim().slice(0, 500);
  setLoading(true);
  try {
    const res = await fetch('api/triage', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Client-Id': 'propcare-site' },
      body: JSON.stringify({ imageBase64: await toBase64(photoBlob), mimeType: 'image/jpeg', description }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok || !data.triage) throw new Error(data.error || 'The photo check is not available right now. Try again later.');
    render(data.triage);
  } catch (e) {
    setLoading(false);
    $('result-empty').hidden = false;
    showError(e instanceof TypeError ? 'No connection. Check your internet and try again.' : e.message);
  } finally {
    setLoading(false);
  }
});

function list(title, items) {
  if (!items || !items.length) return '';
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  return `<h4>${esc(title)}</h4><ul>${items.map((i) => `<li>${esc(i)}</li>`).join('')}</ul>`;
}

function render(t) {
  if (photoUrl) URL.revokeObjectURL(photoUrl);
  photoUrl = URL.createObjectURL(photoBlob);
  $('result-img').src = photoUrl;

  const box = $('result-box');
  const b = Array.isArray(t.box) && t.box.length === 4 ? t.box : null;
  if (b) {
    box.style.top = `${b[0] / 10}%`;
    box.style.left = `${b[1] / 10}%`;
    box.style.height = `${(b[2] - b[0]) / 10}%`;
    box.style.width = `${(b[3] - b[1]) / 10}%`;
  }
  box.hidden = !b;
  $('result-img').alt = b ? 'Your photo, with the problem outlined' : 'Your photo';

  $('r-summary').textContent = t.isPropertyIssue === false ? `${t.issueSummary} This may not be a property problem.` : t.issueSummary;
  const u = $('r-urgency');
  u.textContent = URGENCY[t.urgency] || 'Routine';
  u.className = 'pill ' + (t.urgency === 'emergency' ? 'pill-r' : t.urgency === 'urgent' ? 'pill-y' : '');
  $('r-trade').textContent = TRADES[t.trade] || TRADES.general;
  $('r-emergency').hidden = t.urgency !== 'emergency';
  $('r-lists').innerHTML = list('Likely causes', t.likelyCauses) + list('What you can do now', t.customerAdvice) + list('What a photo cannot show', t.cannotTell);
  const p = PRICE[t.trade] || PRICE.general;
  $('r-price').textContent = `Typically £${p[0]} to £${p[1]}`;

  $('result-empty').hidden = true;
  $('result-card').hidden = false;
  if (window.matchMedia('(max-width: 860px)').matches) $('result').scrollIntoView({ behavior: 'smooth', block: 'start' });
}
