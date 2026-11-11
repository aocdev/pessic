// Verbatim formulas from pressupost/index.html, run headless. Usage: node oracle.js backup.json YYYY-MM-DD
const fs=require("fs");
const store=JSON.parse(fs.readFileSync(process.argv[2],"utf8"));
const [ty,tm,td]=process.argv[3].split("-").map(Number);
const today=new Date(ty,tm-1,td);today.setHours(0,0,0,0);
const CATS=["Habitatge, aigua, llum i gas","Alimentació","Transport","Restaurants i hotels","Oci, esport i cultura","Vestit i calçat","Sanitat","Cura personal i altres","Assegurances i serveis financers","Mobles i llar","Comunicacions","Educació","Alcohol i tabac"];
function pad(n){return String(n).padStart(2,"0");}
function iso(d){return d.getFullYear()+"-"+pad(d.getMonth()+1)+"-"+pad(d.getDate());}
function P(){return store.profiles[store.active];}
function cfg(){return P().cfg;}
function moves(){return P().moves;}
function shift(){return cfg().dia>15?1:0;}
function cycleOf(d){const s=new Date(d.getFullYear(),d.getMonth()-(d.getDate()<cfg().dia?1:0),1);s.setMonth(s.getMonth()+shift());return s.getFullYear()+"-"+pad(s.getMonth()+1);}
function cycleRange(key){const [y,m]=key.split("-").map(Number);const sh=shift(),dia=cfg().dia;return [new Date(y,m-1-sh,dia),new Date(y,m-sh,dia-1)];}
function addCycle(key,n){const [y,m]=key.split("-").map(Number);const d=new Date(y,m-1+n,1);return d.getFullYear()+"-"+pad(d.getMonth()+1);}
function planFor(key){const [s]=cycleRange(key);return cfg().plan.filter(p=>(!p.des||key>=p.des)&&(!p.fins||key<=p.fins)).map(p=>{const d=new Date(s.getFullYear(),s.getMonth(),1);if(p.dia<cfg().dia)d.setMonth(d.getMonth()+1);const last=new Date(d.getFullYear(),d.getMonth()+1,0).getDate();d.setDate(Math.min(p.dia,last));return Object.assign({},p,{date:d,pid:p.id+"@"+key});}).sort((a,b)=>a.date-b.date);}
function reservaMes(){return cfg().anuals.reduce((a,x)=>a+x.imp,0)/12;}
function effLimit(n,key){return (+cfg().limits[n]||0)+planFor(key).filter(p=>p.extra&&p.cat===n).reduce((a,p)=>a+p.imp,0);}
function totalLimits(key){return CATS.reduce((a,c)=>a+effLimit(c,key),0);}
function extraTotal(key){return planFor(key).filter(p=>p.extra).reduce((a,p)=>a+p.imp,0);}
const r2=v=>Math.round(v*100)/100;
const out=[];
const now=cycleOf(today);
for(const cur of [addCycle(now,-1),now,addCycle(now,1),addCycle(now,2)]){
  const C=cfg();const [start,end]=cycleRange(cur);
  const mv=moves().filter(x=>cycleOf(new Date(x.data+"T00:00"))===cur);const mvMes=mv.filter(x=>!x.reserva);
  const plan=planFor(cur);const paid=new Set(mv.filter(x=>x.pid).map(x=>x.pid));
  const lim=totalLimits(cur),res=reservaMes(),extra=extraTotal(cur);
  const spent=mvMes.reduce((a,x)=>a+x.imp,0);
  const pendFix=plan.filter(p=>!paid.has(p.pid)).reduce((a,p)=>a+p.imp,0);
  const fixTotal=plan.reduce((a,p)=>a+p.imp,0);
  const varBudget=Math.max(0,lim-fixTotal);
  const varSpent=mvMes.filter(x=>!x.pid).reduce((a,x)=>a+x.imp,0);
  const left=lim-spent-pendFix;
  const isNow=today>=start&&today<=end;const days=Math.round((end-start)/864e5)+1;
  const daysLeft=isNow?Math.round((end-today)/864e5)+1:(today<start?days:0);
  const elapsed=isNow?(Math.round((today-start)/864e5)+1)/days:(today>end?1:0);
  let st;if(left<-0.004)st="OVER_BUDGET";else if(varSpent>varBudget*elapsed*1.1&&varSpent>20)st="AHEAD_OF_PACE";else st="ON_TRACK";
  const weeks=[];const nW=Math.ceil(days/7);let carry=0;
  for(let i=0;i<nW;i++){const ws=new Date(start);ws.setDate(ws.getDate()+i*7);const we=new Date(ws);we.setDate(we.getDate()+6);if(we>end)we.setTime(end.getTime());
    const nd=Math.round((we-ws)/864e5)+1;const lim2=varBudget/days*nd+carry;
    const sp=mvMes.filter(x=>!x.pid&&x.data>=iso(ws)&&x.data<=iso(we)).reduce((a,x)=>a+x.imp,0);
    const r=lim2-sp,past=we<today,nw=today>=ws&&today<=we;if(past||nw)carry=r;weeks.push(r2(lim2));}
  let bal=C.ingressos-(C.estalvi||0)-res;const balances=plan.map(p=>r2(bal-=p.imp));
  out.push([cur,iso(start),iso(end),r2(lim),r2(extra),r2(res),r2(C.ingressos-lim-res),r2(spent),r2(pendFix),r2(fixTotal),r2(varBudget),r2(varSpent),r2(left),daysLeft,daysLeft>0?r2(left/daysLeft):"",st,plan.length,weeks.join(" "),balances.join(" ")].join("|"));
}
console.log(out.join("\n"));
