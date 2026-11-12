const API = '../api/';
const state = {token: localStorage.getItem('rrAdminToken') || '', users: [], selected: null, central: null};
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
const fmtDate = value => value ? new Intl.DateTimeFormat('hu-HU',{dateStyle:'medium',timeStyle:'short'}).format(new Date(value * 1000)) : '–';
const fmtDuration = ms => { const minutes=Math.max(0,Math.round((ms||0)/60000)); return `${Math.floor(minutes/60)} ó ${String(minutes%60).padStart(2,'0')} p`; };
const fmtKm = meters => `${((meters||0)/1000).toLocaleString('hu-HU',{maximumFractionDigits:1})} km`;
const api = async (path, options={}) => {
  const headers = {'Content-Type':'application/json', ...(options.headers||{})};
  if(state.token) headers.Authorization=`Bearer ${state.token}`;
  const response=await fetch(path,{...options,headers,cache:'no-store'});
  const data=await response.json().catch(()=>({ok:false,error:'Érvénytelen szerverválasz.'}));
  if(!response.ok||!data.ok) throw new Error(data.error||'A kérés nem sikerült.');
  return data;
};

function showLogin(message=''){$('login').hidden=false;$('dashboard').hidden=true;$('login-message').textContent=message;}
function showDashboard(){$('login').hidden=true;$('dashboard').hidden=false;loadUsers();}

$('request-code').onclick=async()=>{
  $('request-code').disabled=true;$('login-message').textContent='Kód küldése…';
  try{await api(API+'request-code.php',{method:'POST',body:JSON.stringify({email:$('email').value,purpose:'admin'})});$('code-area').hidden=false;$('login-message').textContent='A kódot elküldtük.';$('code').focus();}
  catch(error){$('login-message').textContent=error.message;}
  finally{$('request-code').disabled=false;}
};
$('verify-code').onclick=async()=>{
  $('verify-code').disabled=true;$('login-message').textContent='Belépés…';
  try{const data=await api(API+'verify-code.php',{method:'POST',body:JSON.stringify({email:$('email').value,code:$('code').value,purpose:'admin'})});state.token=data.token;localStorage.setItem('rrAdminToken',state.token);showDashboard();}
  catch(error){$('login-message').textContent=error.message;}
  finally{$('verify-code').disabled=false;}
};
$('logout').onclick=()=>{state.token='';localStorage.removeItem('rrAdminToken');showLogin('Sikeresen kijelentkeztél.');};
$('refresh').onclick=()=>state.selected==='central'?loadCentralStops():loadUsers();
$('search').oninput=()=>renderUsers();
$('central-stops').onclick=()=>loadCentralStops();

async function loadUsers(){
  try{
    const data=await api('api.php?action=users');state.users=data.users;renderUsers();
    $('user-count').textContent=state.users.filter(user=>user.role!=='admin').length;
    $('device-count').textContent=state.users.reduce((sum,user)=>sum+user.activeDeviceCount,0);
    const latest=Math.max(0,...state.users.map(user=>user.lastSyncAt||0));$('last-sync').textContent=latest?fmtDate(latest):'Még nincs';
    if(state.selected&&state.selected!=='central') openUser(state.selected);
  }catch(error){if(/lejárt|Bejelentkezés/.test(error.message)){state.token='';localStorage.removeItem('rrAdminToken');showLogin(error.message);}else $('users').innerHTML=`<p class="empty-data">${esc(error.message)}</p>`;}
}
function renderUsers(){
  const term=$('search').value.trim().toLowerCase();
  const users=state.users.filter(user=>`${user.displayName||''} ${user.email}`.toLowerCase().includes(term));
  const statusLabel={ACTIVE:'AKTÍV',PENDING:'VÁRAKOZÓ',BLOCKED:'TILTVA'};
  $('central-stops').classList.toggle('active',state.selected==='central');
  $('users').innerHTML=users.length?users.map(user=>`<button class="user-row ${state.selected===user.id?'active':''}" data-user="${esc(user.id)}"><strong>${esc(user.displayName||user.email)}</strong>${user.displayName?`<span>${esc(user.email)}</span>`:''}<span>${user.deviceCount} készülék · utolsó aktivitás: ${fmtDate(user.lastSeenAt)}</span><span class="status access-${(user.accessStatus||'ACTIVE').toLowerCase()}">${statusLabel[user.accessStatus||'ACTIVE']}</span><span class="status ${user.onlineDeviceCount?'on':'off'}">${user.onlineDeviceCount?'● ONLINE':'OFFLINE'}</span>${user.openWorkCount?'<span class="status working">NYITOTT MUNKA</span>':''}</button>`).join(''):'<p class="empty-data">Nincs találat.</p>';
  document.querySelectorAll('[data-user]').forEach(button=>button.onclick=()=>openUser(button.dataset.user));
}
const stopPhoto = (value,label) => typeof value==='string'&&value.startsWith('data:image/')?`<figure><img src="${esc(value)}" alt="${esc(label)}" loading="lazy"><figcaption>${esc(label)}</figcaption></figure>`:'';
const centralStopCard = place => `<details class="stop-admin"><summary><div><strong>${esc(place.name||'Névtelen megálló')}</strong><small>${esc(place.type==='BAKERY'||place.type==='HOME'?'Bázis':'Megálló')}</small></div><span>${esc(place.officialAddress||'Nincs cím')}</span></summary><div class="stop-info"><div class="stop-info-grid"><p><b>GPS-koordináta</b><span>${place.latitude??'–'}, ${place.longitude??'–'}</span></p><p><b>GPS pontosítva</b><span>${place.gpsManuallyConfirmed?'Igen':'Nem'}</span></p><p><b>Kapukód</b><span>${esc(place.gateCode||'–')}</span></p><p><b>Felismerési sugár</b><span>${place.recognitionRadiusMeters??40} méter</span></p><p><b>Állapot</b><span>${place.active===false?'Inaktív':'Aktív'}</span></p><p><b>Túrasorrend</b><span>${esc(place.defaultTourAnchor||'Nincs')} · ${place.defaultTourOrder??0}</span></p></div><p><b>Megjegyzés:</b> ${esc(place.note||'–')}</p><div class="stop-photos">${stopPhoto(place.photoPreview,'Kulcsfotó')}${stopPhoto(place.entrancePhotoPreview,'Bejáratfotó')}</div></div></details>`;
async function loadCentralStops(){
  state.selected='central';renderUsers();$('detail').className='detail';$('detail').innerHTML='<p class="empty-data">Központi megállótár betöltése…</p>';
  try{const data=await api('api.php?action=central-stops');state.central=data;renderCentralStops(data);}
  catch(error){$('detail').innerHTML=`<p class="empty-data">${esc(error.message)}</p>`;}
}
function renderCentralStops(data){
  const stops=data.stops||[];const active=stops.filter(stop=>stop.active!==false).length;const bases=stops.filter(stop=>stop.type==='BAKERY'||stop.type==='HOME').length;const missingGps=stops.filter(stop=>!stop.gpsManuallyConfirmed).length;
  $('central-stop-count').textContent=`${stops.length} megálló`;
  $('detail').innerHTML=`<div class="detail-header central-header"><div><h2>Központi megállótár</h2><p>Utolsó frissítés: ${fmtDate(data.updatedAt)}</p></div><button id="refresh-central" class="ghost-central">↻ Frissítés</button></div><div class="cards central-metrics"><div class="metric"><span>Összes megálló</span><strong>${stops.length}</strong></div><div class="metric"><span>Aktív</span><strong>${active}</strong></div><div class="metric"><span>Bázis</span><strong>${bases}</strong></div><div class="metric"><span>Nem pontosított GPS</span><strong>${missingGps}</strong></div></div><div class="central-tools"><input id="central-search" type="search" placeholder="Keresés név, cím vagy település alapján"><span>A központi tár az admin jogosultságú alkalmazásból frissíthető.</span></div><div id="central-stop-list" class="stop-admin-list"></div>`;
  const render=()=>{const term=$('central-search').value.trim().toLowerCase();const filtered=stops.filter(stop=>`${stop.name||''} ${stop.officialAddress||''}`.toLowerCase().includes(term));$('central-stop-list').innerHTML=filtered.length?filtered.map(centralStopCard).join(''):'<p class="empty-data">Nincs találat.</p>';};
  $('central-search').oninput=render;$('refresh-central').onclick=loadCentralStops;render();
}
async function openUser(userId){
  state.selected=userId;renderUsers();$('detail').className='detail';$('detail').innerHTML='<p class="empty-data">Adatok betöltése…</p>';
  try{const data=await api(`api.php?action=detail&userId=${encodeURIComponent(userId)}`);renderDetail(data);}
  catch(error){$('detail').innerHTML=`<p class="empty-data">${esc(error.message)}</p>`;}
}
function snapshotSummary(snapshot){
  const data=snapshot?.data||{};const days=data.days||[];let work=0,travel=0,distance=0,stops=0;
  for(const day of days){const events=[...(day.events||[])].sort((a,b)=>a.timestamp-b.timestamp);const ws=events.find(e=>e.type==='WORK_START');const we=[...events].reverse().find(e=>e.type==='WORK_END');if(ws&&we)work+=Math.max(0,we.timestamp-ws.timestamp);let start=null;for(const event of events){if(event.type==='TRIP_START')start=event.timestamp;if(event.type==='TRIP_END'&&start){travel+=Math.max(0,event.timestamp-start);start=null;}}distance+=(day.trips||[]).reduce((sum,trip)=>sum+(trip.distanceMeters||0),0);stops+=(data.plans||[]).filter(plan=>plan.workDayId===day.id&&plan.visited).length;}
  const hourlyRate=data.settings?.hourlyRate||0;return{days,work,travel,distance,stops,earnings:Math.round(work/3600000*hourlyRate)};
}
function renderDetail(data){
  const user=data.user,snapshot=data.snapshot,summary=snapshotSummary(snapshot);const days=[...summary.days].sort((a,b)=>String(b.date).localeCompare(String(a.date)));const places=snapshot?.data?.places||[];const plans=snapshot?.data?.plans||[];const visits=snapshot?.data?.visits||[];const fuelings=snapshot?.data?.fuelings||[];const onlineDevices=data.devices.filter(device=>(device.lastHeartbeatAt||0)>=Date.now()/1000-120);const live=onlineDevices.find(device=>device.liveState?.workOpen)?.liveState;
  $('detail').innerHTML=`
    <div class="detail-header"><div><h2>${esc(user.displayName||user.email)}</h2><p>${user.displayName?esc(user.email)+' · ':''}Regisztrálva: ${fmtDate(user.createdAt)} · Utolsó aktivitás: ${fmtDate(user.lastSeenAt)}</p><span class="status ${onlineDevices.length?'on':'off'}">${onlineDevices.length?'● ONLINE':'OFFLINE'}</span></div></div>
    <div class="user-settings"><h3>Felhasználói beállítások</h3><div class="settings-grid"><label>Megjelenő név<input id="display-name" value="${esc(user.displayName||'')}" placeholder="Felhasználó neve"></label><label>Státusz<select id="access-status"><option value="ACTIVE">Aktív</option><option value="PENDING">Várakozó – munka indítható, napló és export tiltva</option><option value="BLOCKED">Tiltva – az alkalmazás nem használható</option></select></label><label>Szerepkör<select id="user-role"><option value="user">Felhasználó</option><option value="admin">Adminisztrátor</option></select></label><label>Szinkronizálás<select id="sync-policy"><option value="EVERY_STOP">Minden meglátogatott megálló után</option><option value="WORK_END">Munka lezárásakor</option><option value="APP_ACTIVITY">Alkalmazás megnyitásakor és bezárásakor</option></select></label></div><small>A munka lezárása minden beállításnál szinkronizál.</small><button class="primary save-user" id="save-user">Beállítások mentése</button></div>
    ${live?`<div class="live-work"><div><span>ÉLŐ MUNKA · ${esc(live.workDate||'')}</span><strong>${live.tripOpen?'Úton':'Munka folyamatban'}</strong></div><div class="live-grid"><p><b>Kezdés</b>${live.workStartedAt?new Date(live.workStartedAt).toLocaleTimeString('hu-HU',{hour:'2-digit',minute:'2-digit'}):'–'}</p><p><b>Megállók</b>${live.completedStops||0} / ${live.totalStops||0}</p><p><b>Távolság</b>${fmtKm(live.distanceMeters)}</p><p><b>Jelenlegi</b>${esc(live.currentPlaceName||'Úton')}</p><p><b>Tartózkodási idő</b>${live.currentPlaceArrivedAt?fmtDuration(Date.now()-live.currentPlaceArrivedAt):'–'}</p><p><b>Következő</b>${esc(live.nextPlaceName||'Nincs')}</p></div></div>`:''}
    <div class="tabs"><button class="tab active" data-tab="overview">Áttekintés</button><button class="tab" data-tab="work">Munkák</button><button class="tab" data-tab="stops">Megállók</button><button class="tab" data-tab="devices">Készülékek</button></div>
    <div class="panel" data-panel="overview"><div class="cards"><div class="metric"><span>Munkanapok</span><strong>${days.length}</strong></div><div class="metric"><span>Munkaidő</span><strong>${fmtDuration(summary.work)}</strong></div><div class="metric"><span>Távolság</span><strong>${fmtKm(summary.distance)}</strong></div><div class="metric"><span>Megállók</span><strong>${summary.stops}</strong></div><div class="metric"><span>Útidő</span><strong>${fmtDuration(summary.travel)}</strong></div><div class="metric"><span>Kereset</span><strong>${summary.earnings.toLocaleString('hu-HU')} Ft</strong></div><div class="metric"><span>Utolsó szinkron</span><strong>${snapshot?fmtDate(snapshot.updatedAt):'Nincs'}</strong></div></div>${snapshot?'':'<p class="empty-data">Erről a készülékről még nem érkezett adat.</p>'}</div>
    <div class="panel" data-panel="work" hidden>${days.length?`<div class="table-wrap"><table><thead><tr><th>Dátum</th><th>Munkakezdés</th><th>Út indulás</th><th>Megérkezés</th><th>Munka vége</th><th>Távolság</th><th>Megállók</th></tr></thead><tbody>${days.map(day=>workRow(day,plans,places,visits,fuelings)).join('')}</tbody></table></div>`:'<p class="empty-data">Nincs rögzített munka.</p>'}</div>
    <div class="panel" data-panel="stops" hidden>${places.length?`<div class="stop-import-tools"><div><button id="select-all-stops" class="ghost-central">Mind kijelölése</button><button id="clear-stop-selection" class="ghost-central">Kijelölés törlése</button></div><button id="import-central-stops" class="primary import-central" disabled>Kijelöltek feltöltése a központi tárba</button><small id="stop-selection-count">Nincs kijelölt megálló</small></div><div class="stop-admin-list">${places.map((place,index)=>`<details class="stop-admin user-stop"><summary><input class="central-stop-check" type="checkbox" data-stop-index="${index}" aria-label="${esc(place.name)} kijelölése"><div><strong>${esc(place.name)}</strong><small>${esc(place.type==='BAKERY'||place.type==='HOME'?'Bázis':'Megálló')}</small></div><span>${esc(place.officialAddress)}</span></summary><div class="stop-info"><p><b>Típus:</b> ${esc(place.type)}</p><p><b>GPS:</b> ${place.latitude??'–'}, ${place.longitude??'–'}</p><p><b>Kód:</b> ${esc(place.gateCode||'–')}</p><p><b>Megjegyzés:</b> ${esc(place.note||'–')}</p><p><b>Aktív:</b> ${place.active?'Igen':'Nem'} · <b>Látogatások:</b> ${plans.filter(plan=>plan.placeId===place.id&&plan.visited).length}</p><div class="stop-photos">${place.photoPreview?`<img src="${esc(place.photoPreview)}" alt="Kulcsfotó">`:''}${place.entrancePhotoPreview?`<img src="${esc(place.entrancePhotoPreview)}" alt="Kapufotó">`:''}</div></div></details>`).join('')}</div>`:'<p class="empty-data">Nincs rögzített megálló.</p>'}</div>
    <div class="panel" data-panel="devices" hidden>${data.devices.length?data.devices.map(device=>`<div class="device-card"><div><strong>${esc(device.name||'Android készülék')}</strong><span>${esc(device.appVersion||'Ismeretlen verzió')} · ${esc(device.id.slice(-8))}</span><span>Aktivitás: ${fmtDate(device.lastSeenAt)} · Szinkron: ${fmtDate(device.lastSyncAt)}</span></div><button class="${device.enabled?'danger':'success'}" data-device="${esc(device.id)}" data-enabled="${device.enabled?'0':'1'}">${device.enabled?'Letiltás':'Engedélyezés'}</button></div>`).join(''):'<p class="empty-data">Nincs regisztrált készülék.</p>'}</div>`;
  $('access-status').value=user.accessStatus||'ACTIVE';$('user-role').value=user.role||'user';$('sync-policy').value=user.syncPolicy||'EVERY_STOP';
  $('save-user').onclick=async()=>{try{await api('api.php?action=user-settings',{method:'POST',body:JSON.stringify({userId:user.id,displayName:$('display-name').value,accessStatus:$('access-status').value,role:$('user-role').value,syncPolicy:$('sync-policy').value})});await loadUsers();}catch(error){alert(error.message);}};
  document.querySelectorAll('[data-device]').forEach(button=>button.onclick=()=>toggle('device',button.dataset.device,button.dataset.enabled==='1'));
  document.querySelectorAll('[data-tab]').forEach(button=>button.onclick=()=>{document.querySelectorAll('[data-tab]').forEach(item=>item.classList.toggle('active',item===button));document.querySelectorAll('[data-panel]').forEach(panel=>panel.hidden=panel.dataset.panel!==button.dataset.tab);});
  document.querySelectorAll('[data-day]').forEach(row=>row.onclick=()=>{const detail=document.querySelector(`[data-day-detail="${row.dataset.day}"]`);detail.hidden=!detail.hidden;row.classList.toggle('expanded',!detail.hidden);});
  const stopChecks=[...document.querySelectorAll('.central-stop-check')];
  const updateStopSelection=()=>{const count=stopChecks.filter(item=>item.checked).length;if($('stop-selection-count'))$('stop-selection-count').textContent=count?`${count} megálló kijelölve`:'Nincs kijelölt megálló';if($('import-central-stops'))$('import-central-stops').disabled=count===0;};
  stopChecks.forEach(check=>{check.onclick=event=>event.stopPropagation();check.onchange=updateStopSelection;});
  if($('select-all-stops'))$('select-all-stops').onclick=()=>{stopChecks.forEach(check=>check.checked=true);updateStopSelection();};
  if($('clear-stop-selection'))$('clear-stop-selection').onclick=()=>{stopChecks.forEach(check=>check.checked=false);updateStopSelection();};
  if($('import-central-stops'))$('import-central-stops').onclick=async()=>{const selected=stopChecks.filter(check=>check.checked).map(check=>places[Number(check.dataset.stopIndex)]?.id).filter(value=>value!==undefined&&value!==null);if(!selected.length)return;if(!confirm(`Biztosan feltöltöd a kijelölt ${selected.length} megállót a központi tárba? Az azonos nevű és című meglévő bejegyzések frissülnek.`))return;const button=$('import-central-stops');button.disabled=true;button.textContent='Feltöltés…';try{const result=await api('api.php?action=central-import',{method:'POST',body:JSON.stringify({userId:user.id,placeIds:selected})});alert(`${result.imported} megálló feldolgozva. Új: ${result.added}, frissítve: ${result.updated}.`);state.central=null;stopChecks.forEach(check=>check.checked=false);updateStopSelection();}catch(error){alert(error.message);}finally{button.textContent='Kijelöltek feltöltése a központi tárba';updateStopSelection();}};
}
function workRow(day,plans,places,visits,fuelings=[]){const events=day.events||[];const time=type=>{const event=events.find(item=>item.type===type);return event?new Date(event.timestamp).toLocaleTimeString('hu-HU',{hour:'2-digit',minute:'2-digit'}):'–';};const distance=(day.trips||[]).reduce((sum,trip)=>sum+(trip.distanceMeters||0),0);const byId=Object.fromEntries(places.map(place=>[place.id,place]));const timeline=events.map(event=>({at:event.timestamp,label:{WORK_START:'Munka kezdése',TRIP_START:'Út nyitása',TRIP_END:'Megérkezés a pékséghez',WORK_END:'Munka befejezése'}[event.type]||event.type,kind:'work'}));for(const visit of visits.filter(item=>item.workDayId===day.id)){const name=byId[visit.placeId]?.name||'Ismeretlen megálló';if(visit.arrivalTime)timeline.push({at:visit.arrivalTime,label:`Megérkezés: ${name}`,kind:'stop'});if(visit.departureTime&&visit.departureTime!==visit.arrivalTime)timeline.push({at:visit.departureTime,label:`Továbbindulás: ${name}${visit.dwellDurationMillis?` · ${fmtDuration(visit.dwellDurationMillis)}`:''}`,kind:'stop'});}for(const fueling of fuelings.filter(item=>item.workDayId===day.id))timeline.push({at:fueling.timestamp,label:`Tankolás · ${fueling.locationName||'ismeretlen hely'}`,kind:'fuel'});for(const plan of plans.filter(item=>item.workDayId===day.id&&item.visited&&item.completionMode==='MANUAL'))timeline.push({at:plan.completedAt,label:`Manuális teljesítés: ${byId[plan.placeId]?.name||'Ismeretlen megálló'}`,kind:'manual'});timeline.sort((a,b)=>a.at-b.at);const detail=timeline.length?timeline.map(item=>`<li class="${item.kind}"><time>${new Date(item.at).toLocaleTimeString('hu-HU',{hour:'2-digit',minute:'2-digit'})}</time><span>${esc(item.label)}</span></li>`).join(''):'<li>Nincs részletes esemény.</li>';return `<tr class="work-row" data-day="${day.id}"><td><strong>▸ ${esc(day.date)}</strong></td><td>${time('WORK_START')}</td><td>${time('TRIP_START')}</td><td>${time('TRIP_END')}</td><td>${time('WORK_END')}</td><td>${fmtKm(distance)}</td><td>${plans.filter(plan=>plan.workDayId===day.id&&plan.visited).length}</td></tr><tr class="day-detail" data-day-detail="${day.id}" hidden><td colspan="7"><ol class="timeline">${detail}</ol></td></tr>`;}
async function toggle(type,id,enabled){if(!confirm(enabled?'Biztosan engedélyezed?':'Biztosan letiltod?'))return;try{await api('api.php?action=toggle',{method:'POST',body:JSON.stringify({type,id,enabled})});await loadUsers();}catch(error){alert(error.message);}}

if(state.token)showDashboard();else showLogin();
