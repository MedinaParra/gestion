'use client';

import {useEffect,useRef,useState} from 'react';
import './profile.css';

const WIDTH=720,HEIGHT=240,PHOTO_LIMIT=2*1024*1024,SIGNATURE_LIMIT=200*1024;
const emptyForm={name:'',title:'',rut:''};
const byteLength=data=>Math.ceil((data.split(',')[1]?.length||0)*3/4);
function validRut(value){
  if(!value.trim())return true;
  const compact=value.replace(/[.\s-]/g,'').toUpperCase();
  if(!/^\d{1,8}[0-9K]$/.test(compact))return false;
  const digits=compact.slice(0,-1).replace(/^0+(?=\d)/,'');if(/^0+$/.test(digits))return false;let sum=0,factor=2;
  for(let i=digits.length-1;i>=0;i--){sum+=Number(digits[i])*factor;factor=factor===7?2:factor+1}
  const result=11-(sum%11),check=result===11?'0':result===10?'K':String(result);
  return check===compact.at(-1);
}
function signaturePng(canvas){
  const out=document.createElement('canvas');out.width=WIDTH;out.height=HEIGHT;
  out.getContext('2d').drawImage(canvas,0,0,WIDTH,HEIGHT);
  return out.toDataURL('image/png');
}

export default function Profile({user,onSaved}){
  const [profile,setProfile]=useState(null),[form,setForm]=useState(emptyForm),[loading,setLoading]=useState(true),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
  const [photoDataUrl,setPhotoDataUrl]=useState(''),[signatureDataUrl,setSignatureDataUrl]=useState(''),[removePhoto,setRemovePhoto]=useState(false),[removeSignature,setRemoveSignature]=useState(false),[signatureConsent,setSignatureConsent]=useState(false),[drawing,setDrawing]=useState(false),[photoReading,setPhotoReading]=useState(false),[reload,setReload]=useState(0);
  const canvas=useRef(null),fileInput=useRef(null),controller=useRef(null),fileReader=useRef(null),photoGeneration=useRef(0),mounted=useRef(false),pointer=useRef(null),lastPoint=useRef(null),distance=useRef(0),saving=useRef(false),loaded=useRef(false);
  const assetUrl=kind=>`/api/profile/asset?kind=${kind}&v=${encodeURIComponent(profile?.revision||'current')}`;

  function clearDrawing(){
    const c=canvas.current;if(c){const ctx=c.getContext('2d');ctx.save();ctx.setTransform(1,0,0,1,0,0);ctx.clearRect(0,0,c.width,c.height);ctx.restore()}
    pointer.current=null;lastPoint.current=null;distance.current=0;setSignatureDataUrl('');setSignatureConsent(false);setDrawing(false);
  }
  function acceptProfile(value){
    setProfile(value);setForm({name:value?.name??user?.name??'',title:value?.title||'',rut:value?.rut||''});setPhotoDataUrl('');setRemovePhoto(false);setRemoveSignature(false);clearDrawing();if(fileInput.current)fileInput.current.value='';loaded.current=true;
  }
  useEffect(()=>{
    mounted.current=true;loaded.current=false;saving.current=false;setBusy(false);setDrawing(false);setPhotoReading(false);setLoading(true);setError('');setNotice('');setProfile(null);setForm(emptyForm);setPhotoDataUrl('');setSignatureDataUrl('');setRemovePhoto(false);setRemoveSignature(false);setSignatureConsent(false);
    const abort=new AbortController();controller.current=abort;
    (async()=>{
      try{
        const response=await fetch('/api/profile',{cache:'no-store',signal:abort.signal});const data=await response.json();if(!response.ok)throw new Error(data.error||'No se pudo cargar tu perfil.');
        if(abort.signal.aborted)return;
        if(data.userEmail&&String(data.userEmail).toLowerCase()!==String(user?.email||'').toLowerCase())throw new Error('La sesión cambió. Recarga el portal antes de editar tu perfil.');
        acceptProfile(data.profile||{revision:null});
      }catch(e){if(e.name!=='AbortError'&&!abort.signal.aborted)setError(e.message||'No se pudo cargar tu perfil.')}
      finally{if(!abort.signal.aborted)setLoading(false)}
    })();
    return()=>{mounted.current=false;abort.abort();controller.current?.abort();fileReader.current?.abort();photoGeneration.current++;pointer.current=null};
  },[user?.email,reload]);

  useEffect(()=>{
    if(loading||!canvas.current)return;
    const c=canvas.current,dpr=Math.min(window.devicePixelRatio||1,2);c.width=WIDTH*dpr;c.height=HEIGHT*dpr;const ctx=c.getContext('2d');ctx.setTransform(dpr,0,0,dpr,0,0);ctx.strokeStyle='#111820';ctx.fillStyle='#111820';ctx.lineWidth=2.4;ctx.lineCap='round';ctx.lineJoin='round';
    // The logical drawing surface is fixed; CSS resizing never clears a saved stroke.
  },[loading]);

  async function choosePhoto(event){
    const file=event.target.files?.[0];if(!file)return;
    const generation=++photoGeneration.current;fileReader.current?.abort();setError('');setNotice('');
    if(!['image/png','image/jpeg'].includes(file.type)){setError('Elige una foto PNG o JPEG.');event.target.value='';return}
    if(file.size>PHOTO_LIMIT){setError('La foto supera 2 MB. Elige una imagen más pequeña.');event.target.value='';return}
    setPhotoReading(true);
    try{
      const data=await new Promise((resolve,reject)=>{const reader=new FileReader();fileReader.current=reader;reader.onload=()=>resolve(String(reader.result));reader.onerror=()=>reject(new Error('No se pudo leer la foto.'));reader.onabort=()=>reject(new DOMException('Lectura cancelada','AbortError'));reader.readAsDataURL(file)});
      const dimensions=await new Promise((resolve,reject)=>{const image=new Image();image.onload=()=>resolve({width:image.naturalWidth,height:image.naturalHeight});image.onerror=()=>reject(new Error('La imagen no es un PNG/JPEG válido.'));image.src=data});
      if(dimensions.width<1||dimensions.height<1||dimensions.width>4096||dimensions.height>4096)throw new Error('La foto debe medir como máximo 4096 × 4096 píxeles.');
      if(!mounted.current||generation!==photoGeneration.current)return;
      setPhotoDataUrl(data);setRemovePhoto(false);
    }catch(e){if(mounted.current&&generation===photoGeneration.current&&e.name!=='AbortError'){setError(e.message);if(fileInput.current)fileInput.current.value=''}}
    finally{if(mounted.current&&generation===photoGeneration.current)setPhotoReading(false)}
  }
  function point(event){const rect=canvas.current.getBoundingClientRect();return {x:Math.max(2,Math.min(WIDTH-2,(event.clientX-rect.left)*WIDTH/rect.width)),y:Math.max(2,Math.min(HEIGHT-2,(event.clientY-rect.top)*HEIGHT/rect.height))}}
  function startStroke(event){
    if(busy||photoReading||pointer.current!==null||(event.pointerType==='mouse'&&event.button!==0))return;
    event.preventDefault();pointer.current=event.pointerId;lastPoint.current=point(event);canvas.current.setPointerCapture(event.pointerId);setDrawing(true);setSignatureConsent(false);setRemoveSignature(false);setError('');setNotice('');
  }
  function moveStroke(event){
    if(pointer.current!==event.pointerId||!lastPoint.current)return;event.preventDefault();
    const coalesced=event.nativeEvent.getCoalescedEvents?.();const events=coalesced?.length?coalesced:[event];const ctx=canvas.current.getContext('2d');
    for(const sample of events){const next=point(sample),previous=lastPoint.current;ctx.beginPath();ctx.moveTo(previous.x,previous.y);ctx.lineTo(next.x,next.y);ctx.stroke();distance.current+=Math.hypot(next.x-previous.x,next.y-previous.y);lastPoint.current=next}
  }
  function endStroke(event){
    if(pointer.current!==event.pointerId)return;
    pointer.current=null;lastPoint.current=null;setDrawing(false);
    if(canvas.current.hasPointerCapture?.(event.pointerId))canvas.current.releasePointerCapture(event.pointerId);
    if(distance.current<8){setSignatureDataUrl('');setError('El trazado es demasiado corto. Dibuja tu firma o limpia el recuadro para continuar sin reemplazarla.');return}
    try{const data=signaturePng(canvas.current);if(byteLength(data)>SIGNATURE_LIMIT)throw new Error('El trazado supera el tamaño permitido. Límpialo y vuelve a dibujar.');setSignatureDataUrl(data)}catch(e){setSignatureDataUrl('');setError(e.message||'No se pudo preparar el trazado.')}
  }
  function revokeSignature(){
    if(!window.confirm('¿Revocar la firma guardada para usos futuros? Las firmas ya aplicadas a documentos no cambian. Confirma con Guardar perfil.'))return;
    clearDrawing();setRemoveSignature(true);setNotice('Revocación pendiente. Pulsa Guardar perfil para confirmarla.');setError('');
  }
  function removeCurrentPhoto(){
    photoGeneration.current++;fileReader.current?.abort();setPhotoReading(false);setPhotoDataUrl('');setRemovePhoto(true);if(fileInput.current)fileInput.current.value='';setNotice('La foto se quitará al guardar el perfil.');setError('');
  }
  async function save(event){
    event.preventDefault();if(saving.current||drawing||photoReading||!loaded.current)return;
    setError('');setNotice('');
    if(distance.current>0&&!signatureDataUrl){setError('El trazado nuevo no está listo para guardarse. Vuelve a dibujarlo o pulsa Limpiar trazado.');return}
    if(!form.name.trim()){setError('Escribe tu nombre para guardar el perfil.');return}
    if(!validRut(form.rut)){setError('El RUT ingresado no tiene un dígito verificador válido. Revísalo antes de guardar.');return}
    if(signatureDataUrl&&!signatureConsent){setError('Confirma que el trazado es tuyo y autorizas guardarlo antes de continuar.');return}
    setBusy(true);const abort=new AbortController();saving.current=abort;controller.current=abort;
    try{
      const payload={name:form.name.trim(),title:form.title.trim(),rut:form.rut.trim(),expectedRevision:profile?.revision??null,removePhoto,removeSignature,...(photoDataUrl?{photoDataUrl}:{}),...(signatureDataUrl?{signatureDataUrl,signatureConsent:true}:{})};
      const response=await fetch('/api/profile',{method:'PATCH',headers:{'Content-Type':'application/json'},signal:abort.signal,body:JSON.stringify(payload)});const result=await response.json();if(!response.ok)throw new Error(result.error||'No se pudo guardar el perfil.');if(abort.signal.aborted)return;
      if(!result.profile)throw new Error('La respuesta no confirmó el perfil guardado. Recarga el perfil para comprobarlo antes de reenviar.');
      acceptProfile(result.profile);setNotice('Perfil guardado. Ningún documento fue firmado.');if(onSaved)Promise.resolve().then(()=>onSaved(result.profile)).catch(()=>{});
    }catch(e){if(mounted.current&&!abort.signal.aborted)setError(['TypeError','SyntaxError'].includes(e.name)?'No se pudo confirmar el guardado. Recarga el perfil para comprobar su estado antes de reenviar.':e.message||'No se pudo guardar el perfil. Recarga el perfil antes de reenviar.')}
    finally{if(saving.current===abort){saving.current=false;if(mounted.current&&!abort.signal.aborted)setBusy(false)}}
  }
  const disabled=busy||loading||photoReading;
  const photoSrc=removePhoto?'':photoDataUrl||(profile?.photo?.available?assetUrl('photo'):'');
  const savedSignatureSrc=profile?.signature?.available&&!removeSignature?assetUrl('signature'):'';
  if(loading)return <section className="skmProfile" aria-busy="true"><p role="status">Cargando tu perfil privado…</p></section>;
  return <section className="skmProfile" aria-labelledby="profile-title">
    <div className="skmProfileHeading"><div><span className="eyebrow">CUENTA PERSONAL</span><h2 id="profile-title">Mi perfil</h2><p>Datos y firma visual de tu cuenta de Google. Sólo tú puedes editar este perfil.</p></div><button type="button" className="ghost" disabled={disabled} onClick={()=>{if((photoDataUrl||signatureDataUrl||removePhoto||removeSignature||form.name!==(profile?.name??user?.name??'')||form.title!==(profile?.title||'')||form.rut!==(profile?.rut||''))&&!window.confirm('¿Descartar los cambios sin guardar y recargar tu perfil?'))return;setReload(x=>x+1)}}>Recargar perfil</button></div>
    {error&&<p className="skmProfileFeedback skmProfileError" role="alert">{error}</p>}
    {notice&&<p className="skmProfileFeedback" role="status">{notice}</p>}
    {!loaded.current?<div className="skmProfileCard"><p>No se cargaron datos seguros para editar. Recarga tu perfil; no guardaremos un formulario vacío sobre tus datos.</p></div>:<form onSubmit={save} autoComplete="off">
      <fieldset disabled={disabled} className="skmProfileCard"><legend>Identidad y fotografía</legend><div className="skmProfileIdentity">
        <div className="skmProfilePhotoArea"><div className="skmProfilePhoto">{photoSrc?<img src={photoSrc} alt="Vista previa de tu foto de perfil"/>:<span aria-hidden="true">{(form.name||user?.name||'?').slice(0,1).toUpperCase()}</span>}</div><label className="skmProfileFileLabel">Elegir foto<input ref={fileInput} type="file" accept="image/png,image/jpeg" onChange={choosePhoto}/></label><small>PNG o JPEG · máximo 2 MB</small>{(photoSrc||profile?.photo?.available)&&!removePhoto&&<button type="button" className="skmProfileTextButton" onClick={removeCurrentPhoto}>Quitar foto</button>}{removePhoto&&<span className="skmProfilePending">Se quitará al guardar</span>}{photoReading&&<span role="status">Preparando foto…</span>}</div>
        <div className="skmProfileFields"><label>Correo de la cuenta<input readOnly value={user?.email||''} aria-readonly="true"/></label><label>Nombre para el sello<input value={form.name} maxLength={120} required onChange={e=>setForm({...form,name:e.target.value})} autoComplete="off"/></label><label>Cargo<input value={form.title} maxLength={80} onChange={e=>setForm({...form,title:e.target.value})} autoComplete="off"/></label><label>RUT<input value={form.rut} maxLength={16} onChange={e=>setForm({...form,rut:e.target.value})} autoComplete="off" spellCheck={false} autoCapitalize="characters" aria-describedby="profile-rut-help"/></label><small id="profile-rut-help">Ingresa tú mismo tu RUT. Se valida el formato y dígito verificador; esto no verifica tu identidad civil.</small></div>
      </div></fieldset>
      <fieldset disabled={disabled} className="skmProfileCard"><legend>Firma dibujada</legend><p className="skmProfileHelp">Dibuja con el mouse, lápiz o dedo dentro del recuadro. Guardar el trazado <strong>no firma documentos</strong>: cada aplicación requerirá tu confirmación como titular de la sesión.</p>
        {savedSignatureSrc&&<div className="skmProfileSavedSignature"><div><strong>Firma guardada actualmente</strong><span>No se reemplaza hasta guardar un trazado nuevo.</span></div><img src={savedSignatureSrc} alt="Tu firma guardada para usos futuros"/><button type="button" className="skmProfileDanger" onClick={revokeSignature}>Revocar firma guardada</button></div>}
        {removeSignature&&<p className="skmProfilePending">Revocación pendiente: se confirmará con Guardar perfil. Los documentos históricos no cambian.</p>}
        <div className="skmProfileCanvasWrap"><canvas ref={canvas} onPointerDown={startStroke} onPointerMove={moveStroke} onPointerUp={endStroke} onPointerCancel={endStroke} onLostPointerCapture={endStroke} role="img" aria-label="Recuadro para dibujar una firma nueva con mouse, lápiz o dedo" aria-describedby="profile-drawing-help"/><span aria-hidden="true">Dibuja aquí tu firma nueva</span></div>
        <div className="skmProfileDrawingActions"><small id="profile-drawing-help">Limpiar borra sólo el trazado nuevo, no la firma que ya guardaste.</small><button type="button" className="ghost" disabled={busy||drawing} onClick={clearDrawing}>Limpiar trazado</button></div>
        {signatureDataUrl&&<div className="skmProfilePreview"><strong>Vista previa del trazado que guardarás</strong><img src={signatureDataUrl} alt="Vista previa de tu nuevo trazado"/></div>}
        {signatureDataUrl&&<label className="skmProfileConsent"><input type="checkbox" checked={signatureConsent} onChange={e=>setSignatureConsent(e.target.checked)}/><span>Confirmo que este trazado es mío y autorizo guardarlo en mi perfil privado para futuros actos que confirmaré por separado.</span></label>}
        <p className="skmProfilePrivacy">Es una representación visual, no una firma electrónica avanzada ni una firma criptográfica. Cambiar o revocar tu perfil no altera documentos ni firmas históricas.</p>
      </fieldset>
      <div className="skmProfileSave"><p>La foto y el trazado se guardan en almacenamiento privado. El RUT y los datos que confirmes al firmar podrán quedar incorporados al documento y su historial.</p><button type="submit" className="primary" disabled={disabled||drawing||Boolean(signatureDataUrl&&!signatureConsent)}>{busy?'Guardando perfil…':'Guardar perfil'}</button></div>
    </form>}
  </section>;
}
