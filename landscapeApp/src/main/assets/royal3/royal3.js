(() => {
  'use strict';

  const W = 1280, H = 720, REELS = 5, ROWS = 3;
  const CELL_W = 148, CELL_H = 142;
  const REEL_X = 226, REEL_Y = 157;
  const SYMBOLS = ['A','K','Q','J','7','BAR','BELL','DIAMOND','WILD'];
  const SYMBOL_COLORS = [0xf04438,0x22c55e,0xd946ef,0x3b82f6,0xff253f,0xe4bd67,0xffb21c,0x38c9ff,0xffd45a];
  const native = window.RoyalNative || {
    getState: () => JSON.stringify({credits:5000,betPerLine:5,totalBet:100,freeSpins:0}),
    requestSpin: () => JSON.stringify({credits:5000,betPerLine:5,totalBet:100,payout:600,freeSpins:0,feature:false,anticipation:true,board:[[8,0,2],[8,5,7],[8,4,0],[6,2,1],[7,8,5]],wins:[{line:0,rows:[1,1,1,1,1],symbol:8,count:3,amount:600}]}),
    changeBet: () => '{}', reportReady:()=>{}, reportError:()=>{}, vibrate:()=>{}
  };

  let app, root, world, effects, ui, reelLayer, reelMask, winLayer;
  let symbolTextures = [];
  let reels = [];
  let particles = [];
  let state = { credits:5000, betPerLine:5, totalBet:100, freeSpins:0 };
  let spinning = false;
  let audioEnabled = true;
  let audio;
  let bloomFilter = null;
  let rgbFilter = null;
  let sceneTime = 0;
  let shake = 0;
  let winAmount = 0;
  let shownWin = 0;
  let winStart = 0;

  class AudioEngine {
    constructor(){ this.ctx=null; this.master=null; this.hum=null; }
    ensure(){
      if(this.ctx) { if(this.ctx.state==='suspended') this.ctx.resume(); return true; }
      try {
        const C = window.AudioContext || window.webkitAudioContext;
        this.ctx = new C({latencyHint:'interactive'});
        this.master = this.ctx.createGain();
        this.master.gain.value = 0.38;
        this.master.connect(this.ctx.destination);
        this.startHum();
        return true;
      } catch(e){ return false; }
    }
    tone(freq,duration,when=0,type='sine',gain=.1,slide=0){
      if(!audioEnabled || !this.ensure()) return;
      const t=this.ctx.currentTime+when, o=this.ctx.createOscillator(), g=this.ctx.createGain();
      o.type=type; o.frequency.setValueAtTime(freq,t); if(slide) o.frequency.exponentialRampToValueAtTime(Math.max(30,freq+slide),t+duration);
      g.gain.setValueAtTime(.0001,t); g.gain.exponentialRampToValueAtTime(gain,t+.015); g.gain.exponentialRampToValueAtTime(.0001,t+duration);
      o.connect(g); g.connect(this.master); o.start(t); o.stop(t+duration+.03);
    }
    noise(duration=.2, when=0, gain=.12, cutoff=1800){
      if(!audioEnabled || !this.ensure()) return;
      const sr=this.ctx.sampleRate, len=Math.max(1,Math.floor(sr*duration)), b=this.ctx.createBuffer(1,len,sr), d=b.getChannelData(0);
      for(let i=0;i<len;i++) d[i]=(Math.random()*2-1)*(1-i/len);
      const s=this.ctx.createBufferSource(), f=this.ctx.createBiquadFilter(), g=this.ctx.createGain(), t=this.ctx.currentTime+when;
      s.buffer=b; f.type='bandpass'; f.frequency.value=cutoff; f.Q.value=.8; g.gain.setValueAtTime(gain,t); g.gain.exponentialRampToValueAtTime(.0001,t+duration);
      s.connect(f); f.connect(g); g.connect(this.master); s.start(t);
    }
    startHum(){
      if(!this.ctx || this.hum) return;
      const o1=this.ctx.createOscillator(),o2=this.ctx.createOscillator(),g=this.ctx.createGain();
      o1.type='sine';o1.frequency.value=42;o2.type='triangle';o2.frequency.value=84;g.gain.value=.018;
      o1.connect(g);o2.connect(g);g.connect(this.master);o1.start();o2.start();this.hum=[o1,o2,g];
    }
    spin(){ this.noise(1.15,0,.16,980); this.tone(58,.75,0,'sawtooth',.12,260); this.tone(110,.45,.05,'triangle',.08,700); }
    stop(i,last){ const p=90+i*22; this.tone(p,.22,0,'sine',last?.22:.13,-28); this.tone(950+i*130,.07,0,'square',.045,-500); this.noise(.09,0,last?.13:.07,1250+i*180); }
    anticipation(){ for(let i=0;i<5;i++){this.tone(120+i*24,.16,i*.22,'sine',.08,40);this.tone(48,.12,i*.22,'sine',.10,-5);} }
    win(amount,feature){
      const scale=feature?[196,247,294,392,494,588]:amount>1500?[165,220,277,330,440,554]:[220,277,330,440];
      scale.forEach((f,i)=>{this.tone(f,.36,i*.085,'triangle',.12,f*.48);this.tone(f/2,.42,i*.085,'sine',.08,20)});
      this.noise(.32,.02,.11,3200);
    }
    click(){this.tone(520,.08,0,'triangle',.05,180)}
  }

  const easeOutCubic=t=>1-Math.pow(1-t,3);
  const easeOutBack=t=>{const c1=1.70158,c3=c1+1;return 1+c3*Math.pow(t-1,3)+c1*Math.pow(t-1,2)};
  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  const rand=(a,b)=>a+Math.random()*(b-a);

  async function boot(){
    try {
      audio = new AudioEngine();
      app = new PIXI.Application();
      await app.init({
        resizeTo: window, antialias:true, backgroundAlpha:0,
        autoDensity:true, resolution:Math.min(window.devicePixelRatio||1,1.5),
        preference:['webgl','canvas'], powerPreference:'high-performance'
      });
      document.getElementById('stage').appendChild(app.canvas);
      buildScene();
      resize(); window.addEventListener('resize',resize);
      app.ticker.add(tick);
      document.getElementById('boot').classList.add('hide');
      setTimeout(()=>document.getElementById('boot')?.remove(),700);
      try { native.reportReady('pixi-'+(PIXI.VERSION||'8')); } catch(e){}
    } catch(error){
      document.getElementById('boot').innerHTML='<strong>ROYAL SPIN</strong><span>RENDERER DE RESPALDO</span>';
      try { native.reportError(String(error && (error.stack||error.message)||error)); } catch(e){}
    }
  }

  function buildScene(){
    root = new PIXI.Container(); world = new PIXI.Container(); effects = new PIXI.Container(); ui = new PIXI.Container();
    root.addChild(world,effects,ui); app.stage.addChild(root);
    buildBackground(); buildCabinet(); buildSymbols(); buildReels(); buildInterface(); buildFilters();
    readState();
  }

  function resize(){
    const s=Math.min(app.screen.width/W,app.screen.height/H);
    root.scale.set(s); root.position.set((app.screen.width-W*s)/2,(app.screen.height-H*s)/2);
  }

  function gRect(x,y,w,h,r,fill,alpha=1,stroke=null,sw=0){
    const g=new PIXI.Graphics().roundRect(x,y,w,h,r).fill({color:fill,alpha});
    if(stroke!==null) g.stroke({color:stroke,width:sw,alpha:Math.min(1,alpha+.15)});
    return g;
  }

  function buildBackground(){
    const bg=new PIXI.Graphics().rect(0,0,W,H).fill(0x02030a); world.addChild(bg);
    for(let i=0;i<8;i++){
      const glow=new PIXI.Graphics().circle(0,0,180+i*38).fill({color:i%2?0x5b18b5:0x013c70,alpha:.035});
      glow.position.set(180+i*155,120+(i%3)*210); glow.blendMode='add'; world.addChild(glow);
    }
    const grid=new PIXI.Graphics();
    for(let x=-300;x<W+300;x+=56) grid.moveTo(x,520).lineTo(640+(x-640)*1.7,H).stroke({color:0x6d35a4,width:1,alpha:.14});
    for(let y=520;y<H;y+=28) grid.moveTo(0,y).lineTo(W,y).stroke({color:0x19aee8,width:1,alpha:.10});
    world.addChild(grid);
    const stars=new PIXI.Container(); stars.label='stars';
    for(let i=0;i<90;i++){
      const s=new PIXI.Graphics().circle(0,0,i%12===0?2.2:1).fill({color:i%5===0?0x67e7ff:0xffd47c,alpha:rand(.2,.85)});
      s.position.set(rand(0,W),rand(0,H)); s._speed=rand(.08,.42); s._phase=rand(0,6.28); stars.addChild(s);
    }
    world.addChild(stars);
    for(let i=0;i<5;i++){
      const beam=new PIXI.Graphics().poly([0,0,85,0,380,720,-210,720]).fill({color:i%2?0x934cff:0x24c8ff,alpha:.035});
      beam.position.set(i*330-160,0);beam.rotation=rand(-.08,.08);beam.blendMode='add';beam._phase=i;world.addChild(beam);
    }
  }

  function buildCabinet(){
    const shadow=gRect(190,115,830,530,42,0x000000,.75);shadow.filters=[new PIXI.BlurFilter({strength:18,quality:3})];world.addChild(shadow);
    const outer=gRect(184,106,842,530,38,0x190b24,1,0xffd55f,5);world.addChild(outer);
    const rim=gRect(195,119,820,505,31,0x080b16,1,0x6844a0,3);world.addChild(rim);
    const neon=gRect(204,129,802,484,27,0x04050b,1,0x38d9ff,2);neon.blendMode='add';world.addChild(neon);
    const title=new PIXI.Text({text:'ROYAL SPIN',style:{fontFamily:'Georgia',fontSize:64,fontWeight:'900',fill:0xffdd75,stroke:{color:0x542100,width:8},dropShadow:{color:0xff8a00,blur:18,alpha:.8,distance:0}}});
    title.anchor.set(.5);title.position.set(605,70);world.addChild(title);
    const sub=new PIXI.Text({text:'WEBGL CINEMATIC EDITION',style:{fontFamily:'Arial',fontSize:11,fontWeight:'700',letterSpacing:5,fill:0x9beaff}});sub.anchor.set(.5);sub.position.set(605,112);world.addChild(sub);
    for(let i=0;i<14;i++){
      const bulb=new PIXI.Graphics().circle(0,0,5).fill({color:i%2?0xffd558:0x4de5ff,alpha:.85});bulb.blendMode='add';
      bulb.position.set(218+i*59,132);bulb._phase=i*.38;world.addChild(bulb);
    }
  }

  function buildSymbols(){
    symbolTextures = SYMBOLS.map((name,index)=>{
      const c=new PIXI.Container();
      const plate=new PIXI.Graphics().roundRect(-58,-58,116,116,24).fill({color:0x111323,alpha:.92}).stroke({color:SYMBOL_COLORS[index],width:4,alpha:.95});
      c.addChild(plate);
      const halo=new PIXI.Graphics().circle(0,0,48).fill({color:SYMBOL_COLORS[index],alpha:.12});halo.blendMode='add';c.addChild(halo);
      let txt=name, size=58;
      if(name==='BAR')size=35;if(name==='BELL')size=27;if(name==='DIAMOND')size=18;if(name==='WILD')size=31;
      const t=new PIXI.Text({text:txt,style:{fontFamily:name==='7'?'Arial Black':'Georgia',fontSize:size,fontWeight:'900',fill:SYMBOL_COLORS[index],stroke:{color:0x4a2100,width:name.length>3?4:7},dropShadow:{color:SYMBOL_COLORS[index],blur:11,alpha:.85,distance:0},align:'center'}});
      t.anchor.set(.5);c.addChild(t);
      for(let k=0;k<4;k++){
        const gem=new PIXI.Graphics().poly([0,-6,5,0,0,6,-5,0]).fill({color:k%2?0xffffff:0xffd864,alpha:.8});gem.position.set(Math.cos(k*1.57)*47,Math.sin(k*1.57)*47);c.addChild(gem);
      }
      try{return app.renderer.generateTexture({target:c,resolution:1.3})}catch(e){return app.renderer.generateTexture(c)}
    });
  }

  function buildReels(){
    reelLayer=new PIXI.Container();world.addChild(reelLayer);
    reelMask=new PIXI.Graphics().roundRect(REEL_X,REEL_Y,REELS*CELL_W,ROWS*CELL_H,18).fill(0xffffff);world.addChild(reelMask);reelLayer.mask=reelMask;
    const windowGlow=gRect(REEL_X-8,REEL_Y-8,REELS*CELL_W+16,ROWS*CELL_H+16,22,0x05060c,1,0xffd45a,4);world.addChildAt(windowGlow,world.getChildIndex(reelLayer));
    for(let r=0;r<REELS;r++){
      const holder=new PIXI.Container();holder.position.set(REEL_X+r*CELL_W,REEL_Y);reelLayer.addChild(holder);
      const strip=new PIXI.Container();holder.addChild(strip);
      const reel={holder,strip,sprites:[],stopped:true};
      reels.push(reel); buildStrip(reel,[r%9,(r+2)%9,(r+5)%9]);
      const divider=new PIXI.Graphics().rect(REEL_X+(r+1)*CELL_W-1,REEL_Y,2,ROWS*CELL_H).fill({color:0xffd76a,alpha:.24});world.addChild(divider);
    }
    const topShade=new PIXI.Graphics().rect(REEL_X,REEL_Y,REELS*CELL_W,75).fill({color:0x000000,alpha:.36});topShade.filters=[new PIXI.BlurFilter({strength:8})];world.addChild(topShade);
    const botShade=new PIXI.Graphics().rect(REEL_X,REEL_Y+ROWS*CELL_H-75,REELS*CELL_W,75).fill({color:0x000000,alpha:.36});botShade.filters=[new PIXI.BlurFilter({strength:8})];world.addChild(botShade);
    winLayer=new PIXI.Container();world.addChild(winLayer);
  }

  function buildStrip(reel,finalColumn){
    reel.strip.removeChildren(); reel.sprites.length=0;
    const count=34;
    for(let i=0;i<count;i++){
      let id=Math.floor(Math.random()*symbolTextures.length);
      if(i>=count-3) id=finalColumn[i-(count-3)]??id;
      const s=new PIXI.Sprite(symbolTextures[id]);s.anchor.set(.5);s.position.set(CELL_W/2,i*CELL_H+CELL_H/2);
      reel.strip.addChild(s);reel.sprites.push(s);
    }
    reel.strip.y=-(count-3)*CELL_H;
  }

  function buildInterface(){
    ui.addChild(gRect(20,150,154,465,24,0x090b15,.92,0x7445a5,2));
    ui.addChild(gRect(1035,150,225,465,24,0x090b15,.94,0xffd568,2));
    const jackpot=new PIXI.Text({text:'ROYAL JACKPOT',style:{fontFamily:'Arial',fontSize:12,fontWeight:'800',letterSpacing:3,fill:0xffcf61}});jackpot.anchor.set(.5);jackpot.position.set(97,190);ui.addChild(jackpot);
    const jpValue=new PIXI.Text({text:'1.250.000',style:{fontFamily:'Arial Black',fontSize:24,fill:0xffffff,stroke:{color:0x701f9a,width:4},dropShadow:{color:0xce4cff,blur:14,alpha:.8,distance:0}}});jpValue.anchor.set(.5);jpValue.position.set(97,227);ui.addChild(jpValue);ui.jpValue=jpValue;
    ui.addChild(smallLabel('CRÉDITOS',97,330));
    const credits=valueText('5.000',97,366,25,0xffd86a);ui.addChild(credits);ui.credits=credits;
    ui.addChild(smallLabel('FREE SPINS',97,445));
    const free=valueText('0',97,483,29,0x69eaff);ui.addChild(free);ui.free=free;
    ui.addChild(smallLabel('APUESTA / LÍNEA',1147,190));
    const bet=valueText('5 CR',1147,231,25,0xffd86a);ui.addChild(bet);ui.bet=bet;
    ui.addChild(circleButton(1078,284,'−',()=>changeBet(-1)),circleButton(1216,284,'+',()=>changeBet(1)));
    ui.addChild(smallLabel('APUESTA TOTAL',1147,332));
    const total=valueText('100 CR',1147,365,18,0xffffff);ui.addChild(total);ui.total=total;
    const spin=new PIXI.Container();spin.position.set(1147,475);spin.eventMode='static';spin.cursor='pointer';spin.hitArea=new PIXI.Circle(0,0,94);ui.addChild(spin);ui.spin=spin;
    const halo=new PIXI.Graphics().circle(0,0,91).fill({color:0xff8a19,alpha:.16});halo.filters=[new PIXI.BlurFilter({strength:16})];halo.blendMode='add';spin.addChild(halo);spin.halo=halo;
    const ring=new PIXI.Graphics().circle(0,0,78).stroke({color:0xffe48a,width:7}).circle(0,0,67).stroke({color:0xff9c23,width:3});spin.addChild(ring);spin.ring=ring;
    const core=new PIXI.Graphics().circle(0,0,63).fill(0xb44b08).circle(-10,-13,48).fill({color:0xffc441,alpha:.95});spin.addChild(core);
    const st=new PIXI.Text({text:'GIRAR',style:{fontFamily:'Arial Black',fontSize:24,fill:0xffffff,stroke:{color:0x6d2500,width:6},dropShadow:{color:0xffd64e,blur:10,alpha:.9,distance:0}}});st.anchor.set(.5);spin.addChild(st);
    spin.on('pointerdown',()=>{spin.scale.set(.91);audio.click();try{native.vibrate(25)}catch(e){}});
    spin.on('pointerup',()=>{spin.scale.set(1);startSpin()});spin.on('pointerupoutside',()=>spin.scale.set(1));
    const sound=circleButton(1218,582,'♪',()=>{audioEnabled=!audioEnabled;sound.alpha=audioEnabled?1:.35});ui.addChild(sound);
    const win=valueText('0 CR',605,668,24,0xffde77);ui.addChild(win);ui.win=win;
    const status=smallLabel('TOCA GIRAR PARA ACTIVAR LA EXPERIENCIA',605,640);ui.addChild(status);ui.status=status;
  }

  function smallLabel(text,x,y){const t=new PIXI.Text({text,style:{fontFamily:'Arial',fontSize:11,fontWeight:'700',letterSpacing:2,fill:0xaeb5c8}});t.anchor.set(.5);t.position.set(x,y);return t}
  function valueText(text,x,y,size,color){const t=new PIXI.Text({text,style:{fontFamily:'Arial Black',fontSize:size,fill:color,stroke:{color:0x15101f,width:4},dropShadow:{color,blur:8,alpha:.35,distance:0}}});t.anchor.set(.5);t.position.set(x,y);return t}
  function circleButton(x,y,label,fn){const c=new PIXI.Container();c.position.set(x,y);c.eventMode='static';c.cursor='pointer';c.hitArea=new PIXI.Circle(0,0,31);c.addChild(new PIXI.Graphics().circle(0,0,29).fill(0x211333).stroke({color:0xffd361,width:2}));const t=valueText(label,0,-1,24,0xffffff);c.addChild(t);c.on('pointertap',()=>{audio.click();fn()});return c}

  function buildFilters(){
    try {
      const ns=PIXI.filters||PIXI;
      const Bloom=ns.AdvancedBloomFilter;
      if(Bloom){bloomFilter=new Bloom({threshold:.38,bloomScale:1.25,brightness:1.02,blur:7,quality:3});world.filters=[bloomFilter]}
      const RGB=ns.RGBSplitFilter;
      if(RGB){rgbFilter=new RGB();effects.filters=[rgbFilter];rgbFilter.enabled=false}
    } catch(e){}
  }

  function readState(){ try{state=Object.assign(state,JSON.parse(native.getState()));}catch(e){} updateUI(); }
  function changeBet(delta){if(spinning)return;try{state=Object.assign(state,JSON.parse(native.changeBet(delta)));updateUI()}catch(e){}}
  function updateUI(){ui.credits.text=Number(state.credits||0).toLocaleString('es-CL');ui.bet.text=(state.betPerLine||0)+' CR';ui.total.text=(state.totalBet||0)+' CR';ui.free.text=String(state.freeSpins||0)}

  async function startSpin(){
    if(spinning)return; audio.ensure();
    let result;try{result=JSON.parse(native.requestSpin())}catch(e){return}
    if(result.error){ui.status.text=result.error;return}
    spinning=true; winAmount=0;shownWin=0;ui.win.text='0 CR';ui.status.text='ENERGÍA DE GIRO ACTIVADA';state=Object.assign(state,result);updateUI();
    winLayer.removeChildren(); audio.spin(); launchBurst();
    const anticipation=!!result.anticipation; if(anticipation)setTimeout(()=>triggerAnticipation(),1780);
    const promises=[];
    for(let r=0;r<REELS;r++) promises.push(spinReel(r,result.board[r],1600+r*260+(anticipation&&r===4?850:0),r*85));
    await Promise.all(promises);
    spinning=false; revealWin(result);
  }

  function spinReel(index,column,duration,delay){
    return new Promise(resolve=>{
      const reel=reels[index];buildStrip(reel,column);const start=performance.now()+delay;const initial=reel.strip.y;const end=0;reel.stopped=false;
      const update=now=>{
        if(now<start){requestAnimationFrame(update);return}
        const t=clamp((now-start)/duration,0,1),e=t<.18?Math.pow(t/.18,2)*.13:.13+(1-.13)*easeOutCubic((t-.18)/.82);
        const travel=(1-e)*initial+e*end;const overshoot=Math.sin(t*Math.PI*7)*(1-t)*18;
        reel.strip.y=travel+overshoot;
        const velocity=(1-t)*1.6+.2;reel.sprites.forEach((s,i)=>{const stretch=1+Math.min(.48,velocity*.22);s.scale.set(1/stretch,stretch);s.alpha=.72+.28*t;s.rotation=Math.sin(now*.01+i)*.008*(1-t)});
        if(t<1)requestAnimationFrame(update);else{
          reel.strip.y=0;reel.sprites.slice(-3).forEach(s=>s.scale.set(1));reel.stopped=true;impactReel(index);audio.stop(index,index===4);resolve();
        }
      };requestAnimationFrame(update);
    });
  }

  function impactReel(index){
    shake=Math.max(shake,index===4?12:7);const x=REEL_X+CELL_W*(index+.5),y=REEL_Y+ROWS*CELL_H/2;
    shockwave(x,y,index===4?0xffdc65:0x4de9ff);spawnParticles(x,y,index===4?38:20,index===4?0xffd55f:0x51dcff);
    try{native.vibrate(index===4?55:22)}catch(e){}
  }

  function triggerAnticipation(){
    if(!spinning)return;audio.anticipation();ui.status.text='¡ANTICIPACIÓN ROYAL!';
    const shade=new PIXI.Graphics().rect(0,0,W,H).fill({color:0x02030a,alpha:.66});effects.addChild(shade);fadeOut(shade,1700);
    const x=REEL_X+CELL_W*4.5,y=REEL_Y+ROWS*CELL_H/2;
    for(let i=0;i<8;i++)setTimeout(()=>lightning(x,y,90+i*8),i*150);
    reels[4].holder.scale.set(1.045);reels[4].holder.x-=CELL_W*.0225;setTimeout(()=>{reels[4].holder.scale.set(1);reels[4].holder.x=REEL_X+4*CELL_W},1800);
  }

  function revealWin(result){
    winAmount=result.payout||0;winStart=performance.now();
    if(!winAmount){ui.status.text='GIRA NUEVAMENTE';return}
    audio.win(winAmount,result.feature);ui.status.text=result.feature?'FREE SPINS DESBLOQUEADOS':winAmount>1800?'ROYAL WIN':winAmount>600?'BIG WIN':'LÍNEA GANADORA';
    drawWinningLines(result.wins||[]);celebration(winAmount,result.feature);try{native.vibrate(result.feature?180:95)}catch(e){}
  }

  function drawWinningLines(wins){
    wins.slice(0,4).forEach((w,wi)=>{
      const line=new PIXI.Graphics();winLayer.addChild(line);const color=[0xffd558,0x5be6ff,0xff58cf,0x76ff86][wi%4];
      let progress=0;const draw=()=>{progress=Math.min(1,progress+.035);line.clear();for(let r=0;r<Math.max(1,w.count-1);r++){const x1=REEL_X+CELL_W*(r+.5),y1=REEL_Y+CELL_H*(w.rows[r]+.5),x2=REEL_X+CELL_W*(r+1.5),y2=REEL_Y+CELL_H*(w.rows[r+1]+.5);const seg=clamp(progress*(w.count-1)-r,0,1);line.moveTo(x1,y1).lineTo(x1+(x2-x1)*seg,y1+(y2-y1)*seg).stroke({color,width:8,alpha:.95});}if(progress<1)requestAnimationFrame(draw)};draw();
    });
  }

  function celebration(amount,feature){
    const count=feature?230:amount>1800?180:amount>600?120:70;spawnParticles(605,360,count,feature?0x5eeaff:0xffd45c,true);
    for(let i=0;i<6;i++)setTimeout(()=>shockwave(605,360,i%2?0x59e7ff:0xffd45c,1.15+i*.14),i*180);
    flash(feature?0x70e8ff:0xffe09a,.34);
    const banner=new PIXI.Text({text:feature?'FREE SPINS':amount>1800?'ROYAL WIN':amount>600?'BIG WIN':'WIN',style:{fontFamily:'Arial Black',fontSize:feature?72:86,fill:0xffeea2,stroke:{color:0x6b1a87,width:12},dropShadow:{color:feature?0x30dfff:0xff9a18,blur:28,alpha:1,distance:0}}});banner.anchor.set(.5);banner.position.set(605,360);banner.scale.set(.05);effects.addChild(banner);
    const st=performance.now();const anim=now=>{const t=clamp((now-st)/650,0,1);banner.scale.set(easeOutBack(t));banner.rotation=Math.sin(t*Math.PI)*.035;if(t<1)requestAnimationFrame(anim);else setTimeout(()=>fadeOut(banner,700),1700)};requestAnimationFrame(anim);
  }

  function launchBurst(){
    const x=1147,y=475;shockwave(x,y,0xffd45c,1.5);spawnParticles(x,y,70,0xffa32d);flash(0xffffff,.18);shake=9;
    ui.spin.rotation+=.15;
  }

  function shockwave(x,y,color,maxScale=1){
    const ring=new PIXI.Graphics().circle(0,0,45).stroke({color,width:8,alpha:.95});ring.position.set(x,y);ring.blendMode='add';effects.addChild(ring);
    const st=performance.now();const anim=now=>{const t=clamp((now-st)/520,0,1);ring.scale.set(1+t*7*maxScale);ring.alpha=1-t;ring.rotation=t*.4;if(t<1)requestAnimationFrame(anim);else ring.destroy()};requestAnimationFrame(anim);
  }
  function lightning(x,y,len){
    const g=new PIXI.Graphics();let px=x,py=y-len/2;g.moveTo(px,py);for(let i=1;i<8;i++){px=x+rand(-32,32);py=y-len/2+i*len/7;g.lineTo(px,py)}g.stroke({color:Math.random()>.5?0x58e7ff:0xffdf66,width:rand(2,5),alpha:.95});g.blendMode='add';effects.addChild(g);fadeOut(g,260);
  }
  function flash(color,alpha){const f=new PIXI.Graphics().rect(0,0,W,H).fill({color,alpha});effects.addChild(f);fadeOut(f,250)}
  function fadeOut(obj,duration){const st=performance.now(),a=obj.alpha;const fn=now=>{const t=clamp((now-st)/duration,0,1);obj.alpha=a*(1-t);if(t<1)requestAnimationFrame(fn);else obj.destroy()};requestAnimationFrame(fn)}

  function spawnParticles(x,y,count,color,wide=false){
    for(let i=0;i<count;i++){
      const p=new PIXI.Graphics();const size=rand(2,wide?9:6);if(Math.random()>.45)p.circle(0,0,size).fill({color:Math.random()>.35?color:0xffffff,alpha:1});else p.poly([0,-size,size*.7,0,0,size,-size*.7,0]).fill({color,alpha:1});
      p.position.set(x+rand(-18,18),y+rand(-18,18));p.blendMode='add';effects.addChild(p);const angle=rand(0,Math.PI*2),speed=rand(wide?110:70,wide?520:260);particles.push({view:p,vx:Math.cos(angle)*speed,vy:Math.sin(angle)*speed-(wide?100:30),life:rand(.5,wide?1.8:1.05),gravity:wide?260:180,spin:rand(-8,8)});
    }
  }

  function tick(ticker){
    const dt=Math.min(.033,ticker.deltaMS/1000);sceneTime+=dt;
    const stars=world.getChildByLabel?.('stars');if(stars)stars.children.forEach((s,i)=>{s.y+=s._speed*20*dt;if(s.y>H)s.y=-5;s.alpha=.25+.55*Math.abs(Math.sin(sceneTime*.8+s._phase))});
    ui.spin.ring.rotation+=dt*.65;ui.spin.halo.scale.set(1+.10*Math.sin(sceneTime*3));ui.spin.halo.alpha=.55+.3*Math.sin(sceneTime*4.1);
    world.children.forEach(c=>{if(c._phase!==undefined)c.alpha=.45+.5*Math.abs(Math.sin(sceneTime*1.9+c._phase))});
    if(shake>0.05){world.position.set(rand(-shake,shake),rand(-shake*.55,shake*.55));shake*=Math.pow(.12,dt)}else world.position.set(0,0);
    for(let i=particles.length-1;i>=0;i--){const p=particles[i];p.life-=dt;if(p.life<=0){p.view.destroy();particles.splice(i,1);continue}p.vy+=p.gravity*dt;p.view.x+=p.vx*dt;p.view.y+=p.vy*dt;p.view.rotation+=p.spin*dt;p.view.alpha=clamp(p.life/.55,0,1);p.view.scale.set(.65+.45*p.view.alpha)}
    if(winAmount>0){const t=clamp((performance.now()-winStart)/1500,0,1);shownWin=Math.round(winAmount*easeOutCubic(t));ui.win.text=shownWin.toLocaleString('es-CL')+' CR';if(t>=1)winAmount=0}
    if(bloomFilter){bloomFilter.brightness=1+.035*Math.sin(sceneTime*2.3)}
  }

  boot();
})();
