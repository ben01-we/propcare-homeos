
const $=s=>document.querySelector(s), $$=s=>[...document.querySelectorAll(s)];
const root=document.documentElement, body=document.body, story=$('.story'), visual=$('.hero-visual');
const media=matchMedia('(prefers-reduced-motion: reduce)'); let manual=false,reduced=media.matches,queued=false,pointer=0,chapter=-1;
const clamp=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
function setChapter(n){if(chapter===n)return;chapter=n;$$('.chapter-panel').forEach((el,i)=>el.classList.toggle('active',i===n));$$('.story-phone img').forEach((el,i)=>{el.classList.toggle('active',i===n);el.setAttribute('aria-hidden',i===n?'false':'true')});$$('[data-step]').forEach((el,i)=>{el.classList.toggle('active',i===n);if(i===n)el.setAttribute('aria-current','step');else el.removeAttribute('aria-current')});$('#chip-title').textContent=['A photo. A first look.','A suitable trade. A clear brief.','Completed. Covered. Connected.'][n];$('#chip-copy').textContent=['Capture the issue. Keep the next step simple.','Compare sample profiles, then share the details.','Your original warranty stays linked to the repair.'][n]}
function update(){queued=false;const y=scrollY,h=innerHeight;root.style.setProperty('--progress',clamp(y/(document.documentElement.scrollHeight-h)));if(reduced){setChapter(0);return}const hero=clamp(y/(h*.9));visual.style.setProperty('--turn',(-33+hero*23+pointer*5)+'deg');visual.style.setProperty('--roof-lift',(-hero*90)+'px');visual.style.setProperty('--upper-lift',(-hero*30)+'px');visual.style.setProperty('--lift',(-hero*35)+'px');visual.style.setProperty('--phone-lift',(-hero*60)+'px');const t=clamp(-story.getBoundingClientRect().top/(story.offsetHeight-h));setChapter(Math.min(2,Math.floor(t*3)));$('.story-visual').style.setProperty('--story-turn',(-18+Math.sin(t*Math.PI*2)*17)+'deg');$('.story-visual').style.setProperty('--story-lean',(-7+t*14)+'deg');$('.story-visual').style.setProperty('--chip-lift',(-Math.sin(t*Math.PI)*28)+'px');const photo=$('.photo-panel');photo.style.setProperty('--photo-shift',(-10+clamp((h-photo.getBoundingClientRect().top)/(h+photo.offsetHeight))*13)+'%');const p=clamp((h-$('.protect').getBoundingClientRect().top)/(h+$('.protect').offsetHeight));$('.dial').style.setProperty('--dial-turn',(-30+p*45)+'deg');$('.dial').style.setProperty('--dash',240-p*230)}
function request(){if(!queued){queued=true;requestAnimationFrame(update)}}
addEventListener('scroll',request,{passive:true});addEventListener('resize',request);visual.addEventListener('pointermove',e=>{if(reduced||e.pointerType==='touch')return;const r=visual.getBoundingClientRect();pointer=((e.clientX-r.left)/r.width-.5)*2;request()});visual.addEventListener('pointerleave',()=>{pointer=0;request()});
function applyMotion(){body.classList.toggle('reduced',reduced);$('#motion').setAttribute('aria-pressed',String(reduced));$('#motion').textContent=reduced?'Enable motion':'Reduce motion';if(reduced){for(const name of ['--turn','--roof-lift','--upper-lift','--lift','--phone-lift'])visual.style.removeProperty(name);for(const el of $$('.story-visual,.dial,.photo-panel'))el.removeAttribute('style')}request()}
$('#motion').addEventListener('click',()=>{manual=true;reduced=!reduced;applyMotion()});media.addEventListener('change',e=>{if(!manual){reduced=e.matches;applyMotion()}});$$('[data-step]').forEach(b=>b.addEventListener('click',()=>{const n=+b.dataset.step;const y=story.getBoundingClientRect().top+scrollY+(story.offsetHeight-innerHeight)*((n+.15)/3);scrollTo({top:y,behavior:reduced?'instant':'smooth'})}));applyMotion();

// Manual role tabs: predictable focus order, arrow keys, Home and End.
const roleTabs=$$('.role-tabs [role="tab"]');
function selectRole(tab,focus=false){
 roleTabs.forEach(item=>{
  const selected=item===tab;
  item.setAttribute('aria-selected',String(selected));
  item.tabIndex=selected?0:-1;
  document.getElementById(item.getAttribute('aria-controls')).hidden=!selected;
 });
 if(focus)tab.focus();
 request();
}
roleTabs.forEach((tab,index)=>{
 tab.addEventListener('click',()=>selectRole(tab));
 tab.addEventListener('keydown',event=>{
  let next;
  if(event.key==='ArrowRight')next=(index+1)%roleTabs.length;
  if(event.key==='ArrowLeft')next=(index-1+roleTabs.length)%roleTabs.length;
  if(event.key==='Home')next=0;
  if(event.key==='End')next=roleTabs.length-1;
  if(next!==undefined){event.preventDefault();selectRole(roleTabs[next],true)}
 });
});
