const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const root = document.documentElement, story = $('.story'), visual = $('.hero-visual');
const media = matchMedia('(prefers-reduced-motion: reduce)');
let manual = false, reduced = media.matches, queued = false, pointer = 0, chapter = -1;
const clamp = value => Math.max(0, Math.min(1, value));
function setChapter(index) {
  if (chapter === index) return;
  chapter = index;
  $$('.chapter-panel').forEach((panel, i) => {
    panel.classList.toggle('active', i === index);
    panel.setAttribute('aria-hidden', String(!reduced && i !== index));
  });
  $$('.story-phone img').forEach((img, i) => {
    img.classList.toggle('active', i === index);
    img.setAttribute('aria-hidden', String(i !== index));
  });
  $$('[data-step]').forEach((button, i) => {
    button.classList.toggle('active', i === index);
    if (i === index) button.setAttribute('aria-current', 'step');
    else button.removeAttribute('aria-current');
  });
  $('#chip-title').textContent = ['A photo. A first look.', 'A suitable trade. A clear brief.', 'Completed. Covered. Connected.'][index];
  $('#chip-copy').textContent = ['Capture the issue. Keep the next step simple.', 'Compare sample profiles, then share the details.', 'Your original warranty stays linked to the repair.'][index];
}
function update() {
  queued = false;
  const height = innerHeight;
  root.style.setProperty('--progress', clamp(scrollY / Math.max(1, root.scrollHeight - height)));
  if (reduced) { setChapter(0); return; }
  const scene = $('.app-intro').getBoundingClientRect();
  const progress = clamp((height * .6 - scene.top) / (height + scene.height));
  visual.style.setProperty('--turn', (-33 + progress * 23 + pointer * 5) + 'deg');
  visual.style.setProperty('--roof-lift', (-progress * 90) + 'px');
  visual.style.setProperty('--upper-lift', (-progress * 30) + 'px');
  visual.style.setProperty('--lift', (-progress * 35) + 'px');
  visual.style.setProperty('--phone-lift', (-progress * 60) + 'px');
  const t = clamp(-story.getBoundingClientRect().top / Math.max(1, story.offsetHeight - height));
  setChapter(Math.min(2, Math.floor(t * 3)));
  $('.story-visual').style.setProperty('--story-turn', (-18 + Math.sin(t * Math.PI * 2) * 17) + 'deg');
  $('.story-visual').style.setProperty('--story-lean', (-7 + t * 14) + 'deg');
  $('.story-visual').style.setProperty('--chip-lift', (-Math.sin(t * Math.PI) * 28) + 'px');
  const protection = $('.protect');
  const p = clamp((height - protection.getBoundingClientRect().top) / (height + protection.offsetHeight));
  $('.dial').style.setProperty('--dial-turn', (-30 + p * 45) + 'deg');
  $('.dial').style.setProperty('--dash', 240 - p * 230);
}
function request() { if (!queued) { queued = true; requestAnimationFrame(update); } }
addEventListener('scroll', request, { passive: true });
addEventListener('resize', request);
visual.addEventListener('pointermove', event => {
  if (reduced || event.pointerType === 'touch') return;
  const bounds = visual.getBoundingClientRect();
  pointer = ((event.clientX - bounds.left) / bounds.width - .5) * 2;
  request();
});
visual.addEventListener('pointerleave', () => { pointer = 0; request(); });
function applyMotion() {
  document.body.classList.toggle('reduced', reduced);
  $('#motion').setAttribute('aria-pressed', String(reduced));
  $('#motion').textContent = reduced ? 'Enable motion' : 'Reduce motion';
  if (reduced) {
    for (const property of ['--turn', '--roof-lift', '--upper-lift', '--lift', '--phone-lift']) visual.style.removeProperty(property);
    for (const element of $$('.story-visual,.dial')) element.removeAttribute('style');
  }
  chapter = -1;
  request();
}
$('#motion').addEventListener('click', () => { manual = true; reduced = !reduced; applyMotion(); });
media.addEventListener('change', event => { if (!manual) { reduced = event.matches; applyMotion(); } });
$$('[data-step]').forEach(button => button.addEventListener('click', () => {
  const index = Number(button.dataset.step);
  const top = story.getBoundingClientRect().top + scrollY + (story.offsetHeight - innerHeight) * ((index + .15) / 3);
  scrollTo({ top, behavior: reduced ? 'instant' : 'smooth' });
}));
applyMotion();

const serviceTabs = $$('.service-tabs [role="tab"]');
function selectService(tab, focus = false) {
  serviceTabs.forEach(item => {
    const selected = item === tab;
    item.setAttribute('aria-selected', String(selected));
    item.tabIndex = selected ? 0 : -1;
    document.getElementById(item.getAttribute('aria-controls')).hidden = !selected;
  });
  if (focus) tab.focus();
  request();
}
serviceTabs.forEach((tab, index) => {
  tab.addEventListener('click', () => selectService(tab));
  tab.addEventListener('keydown', event => {
    let next;
    if (event.key === 'ArrowRight') next = (index + 1) % serviceTabs.length;
    if (event.key === 'ArrowLeft') next = (index - 1 + serviceTabs.length) % serviceTabs.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = serviceTabs.length - 1;
    if (next !== undefined) { event.preventDefault(); selectService(serviceTabs[next], true); }
  });
});
const menuButton = $('.menu-toggle'), menu = $('#main-nav');
function closeMenu() { menu.classList.remove('open'); menuButton.setAttribute('aria-expanded', 'false'); menuButton.setAttribute('aria-label', 'Open menu'); }
menuButton.addEventListener('click', () => {
  const open = !menu.classList.contains('open');
  menu.classList.toggle('open', open);
  menuButton.setAttribute('aria-expanded', String(open));
  menuButton.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
});
menu.querySelectorAll('a').forEach(link => link.addEventListener('click', closeMenu));
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && menu.classList.contains('open')) { closeMenu(); menuButton.focus(); }
});
document.addEventListener('click', event => { if (!event.target.closest('header')) closeMenu(); });
matchMedia('(min-width: 701px)').addEventListener('change', event => { if (event.matches) closeMenu(); });
