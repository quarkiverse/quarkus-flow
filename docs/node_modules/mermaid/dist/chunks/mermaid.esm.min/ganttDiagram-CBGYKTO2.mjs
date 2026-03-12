import{m as Ee}from"./chunk-Q3CC2MQB.mjs";import{a as ei}from"./chunk-XBXGYYE5.mjs";import{F as ae,N as oe,Q as ce,R as le,S as ue,T as de,U as fe,V as he,W as me,Y as nt}from"./chunk-3UWU4A3N.mjs";import{A as Ce,B as At,C as Ot,D as Me,a as re,b as et,d as ke,e as ye,f as pe,g as ge,h as yt,i as xe,o as be,p as Et,q as Yt,r as $t,s as Lt,t as It,u as Te,v as ve,w as we,x as _e,y as De,z as Se}from"./chunk-MGPAVIPZ.mjs";import"./chunk-JIN56HTB.mjs";import{a as o,c as vt,f as ot}from"./chunk-VELTKBKT.mjs";var $e=vt((Wt,Pt)=>{"use strict";(function(t,n){typeof Wt=="object"&&typeof Pt<"u"?Pt.exports=n():typeof define=="function"&&define.amd?define(n):(t=typeof globalThis<"u"?globalThis:t||self).dayjs_plugin_isoWeek=n()})(Wt,(function(){"use strict";var t="day";return function(n,r,a){var s=o(function(D){return D.add(4-D.isoWeekday(),t)},"a"),u=r.prototype;u.isoWeekYear=function(){return s(this).year()},u.isoWeek=function(D){if(!this.$utils().u(D))return this.add(7*(D-this.isoWeek()),t);var S,P,C,W,R=s(this),z=(S=this.isoWeekYear(),P=this.$u,C=(P?a.utc:a)().year(S).startOf("year"),W=4-C.isoWeekday(),C.isoWeekday()>4&&(W+=7),C.add(W,t));return R.diff(z,"week")+1},u.isoWeekday=function(D){return this.$utils().u(D)?this.day()||7:this.day(this.day()%7?D:D-7)};var x=u.startOf;u.startOf=function(D,S){var P=this.$utils(),C=!!P.u(S)||S;return P.p(D)==="isoweek"?C?this.date(this.date()-(this.isoWeekday()-1)).startOf("day"):this.date(this.date()-1-(this.isoWeekday()-1)+7).endOf("day"):x.bind(this)(D,S)}}}))});var Le=vt((Vt,Nt)=>{"use strict";(function(t,n){typeof Vt=="object"&&typeof Nt<"u"?Nt.exports=n():typeof define=="function"&&define.amd?define(n):(t=typeof globalThis<"u"?globalThis:t||self).dayjs_plugin_customParseFormat=n()})(Vt,(function(){"use strict";var t={LTS:"h:mm:ss A",LT:"h:mm A",L:"MM/DD/YYYY",LL:"MMMM D, YYYY",LLL:"MMMM D, YYYY h:mm A",LLLL:"dddd, MMMM D, YYYY h:mm A"},n=/(\[[^[]*\])|([-_:/.,()\s]+)|(A|a|Q|YYYY|YY?|ww?|MM?M?M?|Do|DD?|hh?|HH?|mm?|ss?|S{1,3}|z|ZZ?)/g,r=/\d/,a=/\d\d/,s=/\d\d?/,u=/\d*[^-_:/,()\s\d]+/,x={},D=o(function(v){return(v=+v)+(v>68?1900:2e3)},"a"),S=o(function(v){return function(k){this[v]=+k}},"f"),P=[/[+-]\d\d:?(\d\d)?|Z/,function(v){(this.zone||(this.zone={})).offset=(function(k){if(!k||k==="Z")return 0;var O=k.match(/([+-]|\d\d)/g),L=60*O[1]+(+O[2]||0);return L===0?0:O[0]==="+"?-L:L})(v)}],C=o(function(v){var k=x[v];return k&&(k.indexOf?k:k.s.concat(k.f))},"u"),W=o(function(v,k){var O,L=x.meridiem;if(L){for(var G=1;G<=24;G+=1)if(v.indexOf(L(G,0,k))>-1){O=G>12;break}}else O=v===(k?"pm":"PM");return O},"d"),R={A:[u,function(v){this.afternoon=W(v,!1)}],a:[u,function(v){this.afternoon=W(v,!0)}],Q:[r,function(v){this.month=3*(v-1)+1}],S:[r,function(v){this.milliseconds=100*+v}],SS:[a,function(v){this.milliseconds=10*+v}],SSS:[/\d{3}/,function(v){this.milliseconds=+v}],s:[s,S("seconds")],ss:[s,S("seconds")],m:[s,S("minutes")],mm:[s,S("minutes")],H:[s,S("hours")],h:[s,S("hours")],HH:[s,S("hours")],hh:[s,S("hours")],D:[s,S("day")],DD:[a,S("day")],Do:[u,function(v){var k=x.ordinal,O=v.match(/\d+/);if(this.day=O[0],k)for(var L=1;L<=31;L+=1)k(L).replace(/\[|\]/g,"")===v&&(this.day=L)}],w:[s,S("week")],ww:[a,S("week")],M:[s,S("month")],MM:[a,S("month")],MMM:[u,function(v){var k=C("months"),O=(C("monthsShort")||k.map((function(L){return L.slice(0,3)}))).indexOf(v)+1;if(O<1)throw new Error;this.month=O%12||O}],MMMM:[u,function(v){var k=C("months").indexOf(v)+1;if(k<1)throw new Error;this.month=k%12||k}],Y:[/[+-]?\d+/,S("year")],YY:[a,function(v){this.year=D(v)}],YYYY:[/\d{4}/,S("year")],Z:P,ZZ:P};function z(v){var k,O;k=v,O=x&&x.formats;for(var L=(v=k.replace(/(\[[^\]]+])|(LTS?|l{1,4}|L{1,4})/g,(function(I,f,y){var p=y&&y.toUpperCase();return f||O[y]||t[y]||O[p].replace(/(\[[^\]]+])|(MMMM|MM|DD|dddd)/g,(function(T,g,c){return g||c.slice(1)}))}))).match(n),G=L.length,X=0;X<G;X+=1){var E=L[X],b=R[E],d=b&&b[0],M=b&&b[1];L[X]=M?{regex:d,parser:M}:E.replace(/^\[|\]$/g,"")}return function(I){for(var f={},y=0,p=0;y<G;y+=1){var T=L[y];if(typeof T=="string")p+=T.length;else{var g=T.regex,c=T.parser,l=I.slice(p),h=g.exec(l)[0];c.call(f,h),I=I.replace(h,"")}}return(function(m){var w=m.afternoon;if(w!==void 0){var i=m.hours;w?i<12&&(m.hours+=12):i===12&&(m.hours=0),delete m.afternoon}})(f),f}}return o(z,"l"),function(v,k,O){O.p.customParseFormat=!0,v&&v.parseTwoDigitYear&&(D=v.parseTwoDigitYear);var L=k.prototype,G=L.parse;L.parse=function(X){var E=X.date,b=X.utc,d=X.args;this.$u=b;var M=d[1];if(typeof M=="string"){var I=d[2]===!0,f=d[3]===!0,y=I||f,p=d[2];f&&(p=d[2]),x=this.$locale(),!I&&p&&(x=O.Ls[p]),this.$d=(function(l,h,m,w){try{if(["x","X"].indexOf(h)>-1)return new Date((h==="X"?1e3:1)*l);var i=z(h)(l),F=i.year,e=i.month,_=i.day,A=i.hours,Y=i.minutes,$=i.seconds,H=i.milliseconds,V=i.zone,N=i.week,U=new Date,st=_||(F||e?1:U.getDate()),rt=F||U.getFullYear(),lt=0;F&&!e||(lt=e>0?e-1:U.getMonth());var mt,kt=A||0,j=Y||0,at=$||0,K=H||0;return V?new Date(Date.UTC(rt,lt,st,kt,j,at,K+60*V.offset*1e3)):m?new Date(Date.UTC(rt,lt,st,kt,j,at,K)):(mt=new Date(rt,lt,st,kt,j,at,K),N&&(mt=w(mt).week(N).toDate()),mt)}catch{return new Date("")}})(E,M,b,O),this.init(),p&&p!==!0&&(this.$L=this.locale(p).$L),y&&E!=this.format(M)&&(this.$d=new Date("")),x={}}else if(M instanceof Array)for(var T=M.length,g=1;g<=T;g+=1){d[1]=M[g-1];var c=O.apply(this,d);if(c.isValid()){this.$d=c.$d,this.$L=c.$L,this.init();break}g===T&&(this.$d=new Date(""))}else G.call(this,X)}}}))});var Ie=vt((zt,Rt)=>{"use strict";(function(t,n){typeof zt=="object"&&typeof Rt<"u"?Rt.exports=n():typeof define=="function"&&define.amd?define(n):(t=typeof globalThis<"u"?globalThis:t||self).dayjs_plugin_advancedFormat=n()})(zt,(function(){"use strict";return function(t,n){var r=n.prototype,a=r.format;r.format=function(s){var u=this,x=this.$locale();if(!this.isValid())return a.bind(this)(s);var D=this.$utils(),S=(s||"YYYY-MM-DDTHH:mm:ssZ").replace(/\[([^\]]+)]|Q|wo|ww|w|WW|W|zzz|z|gggg|GGGG|Do|X|x|k{1,2}|S/g,(function(P){switch(P){case"Q":return Math.ceil((u.$M+1)/3);case"Do":return x.ordinal(u.$D);case"gggg":return u.weekYear();case"GGGG":return u.isoWeekYear();case"wo":return x.ordinal(u.week(),"W");case"w":case"ww":return D.s(u.week(),P==="w"?1:2,"0");case"W":case"WW":return D.s(u.isoWeek(),P==="W"?1:2,"0");case"k":case"kk":return D.s(String(u.$H===0?24:u.$H),P==="k"?1:2,"0");case"X":return Math.floor(u.$d.getTime()/1e3);case"x":return u.$d.getTime();case"z":return"["+u.offsetName()+"]";case"zzz":return"["+u.offsetName("long")+"]";default:return P}}));return a.bind(this)(S)}}}))});var qe=vt((ee,ie)=>{"use strict";(function(t,n){typeof ee=="object"&&typeof ie<"u"?ie.exports=n():typeof define=="function"&&define.amd?define(n):(t=typeof globalThis<"u"?globalThis:t||self).dayjs_plugin_duration=n()})(ee,(function(){"use strict";var t,n,r=1e3,a=6e4,s=36e5,u=864e5,x=/\[([^\]]+)]|Y{1,4}|M{1,4}|D{1,2}|d{1,4}|H{1,2}|h{1,2}|a|A|m{1,2}|s{1,2}|Z{1,2}|SSS/g,D=31536e6,S=2628e6,P=/^(-|\+)?P(?:([-+]?[0-9,.]*)Y)?(?:([-+]?[0-9,.]*)M)?(?:([-+]?[0-9,.]*)W)?(?:([-+]?[0-9,.]*)D)?(?:T(?:([-+]?[0-9,.]*)H)?(?:([-+]?[0-9,.]*)M)?(?:([-+]?[0-9,.]*)S)?)?$/,C={years:D,months:S,days:u,hours:s,minutes:a,seconds:r,milliseconds:1,weeks:6048e5},W=o(function(E){return E instanceof G},"c"),R=o(function(E,b,d){return new G(E,d,b.$l)},"f"),z=o(function(E){return n.p(E)+"s"},"m"),v=o(function(E){return E<0},"l"),k=o(function(E){return v(E)?Math.ceil(E):Math.floor(E)},"$"),O=o(function(E){return Math.abs(E)},"y"),L=o(function(E,b){return E?v(E)?{negative:!0,format:""+O(E)+b}:{negative:!1,format:""+E+b}:{negative:!1,format:""}},"v"),G=(function(){function E(d,M,I){var f=this;if(this.$d={},this.$l=I,d===void 0&&(this.$ms=0,this.parseFromMilliseconds()),M)return R(d*C[z(M)],this);if(typeof d=="number")return this.$ms=d,this.parseFromMilliseconds(),this;if(typeof d=="object")return Object.keys(d).forEach((function(T){f.$d[z(T)]=d[T]})),this.calMilliseconds(),this;if(typeof d=="string"){var y=d.match(P);if(y){var p=y.slice(2).map((function(T){return T!=null?Number(T):0}));return this.$d.years=p[0],this.$d.months=p[1],this.$d.weeks=p[2],this.$d.days=p[3],this.$d.hours=p[4],this.$d.minutes=p[5],this.$d.seconds=p[6],this.calMilliseconds(),this}}return this}o(E,"l");var b=E.prototype;return b.calMilliseconds=function(){var d=this;this.$ms=Object.keys(this.$d).reduce((function(M,I){return M+(d.$d[I]||0)*C[I]}),0)},b.parseFromMilliseconds=function(){var d=this.$ms;this.$d.years=k(d/D),d%=D,this.$d.months=k(d/S),d%=S,this.$d.days=k(d/u),d%=u,this.$d.hours=k(d/s),d%=s,this.$d.minutes=k(d/a),d%=a,this.$d.seconds=k(d/r),d%=r,this.$d.milliseconds=d},b.toISOString=function(){var d=L(this.$d.years,"Y"),M=L(this.$d.months,"M"),I=+this.$d.days||0;this.$d.weeks&&(I+=7*this.$d.weeks);var f=L(I,"D"),y=L(this.$d.hours,"H"),p=L(this.$d.minutes,"M"),T=this.$d.seconds||0;this.$d.milliseconds&&(T+=this.$d.milliseconds/1e3,T=Math.round(1e3*T)/1e3);var g=L(T,"S"),c=d.negative||M.negative||f.negative||y.negative||p.negative||g.negative,l=y.format||p.format||g.format?"T":"",h=(c?"-":"")+"P"+d.format+M.format+f.format+l+y.format+p.format+g.format;return h==="P"||h==="-P"?"P0D":h},b.toJSON=function(){return this.toISOString()},b.format=function(d){var M=d||"YYYY-MM-DDTHH:mm:ss",I={Y:this.$d.years,YY:n.s(this.$d.years,2,"0"),YYYY:n.s(this.$d.years,4,"0"),M:this.$d.months,MM:n.s(this.$d.months,2,"0"),D:this.$d.days,DD:n.s(this.$d.days,2,"0"),H:this.$d.hours,HH:n.s(this.$d.hours,2,"0"),m:this.$d.minutes,mm:n.s(this.$d.minutes,2,"0"),s:this.$d.seconds,ss:n.s(this.$d.seconds,2,"0"),SSS:n.s(this.$d.milliseconds,3,"0")};return M.replace(x,(function(f,y){return y||String(I[f])}))},b.as=function(d){return this.$ms/C[z(d)]},b.get=function(d){var M=this.$ms,I=z(d);return I==="milliseconds"?M%=1e3:M=I==="weeks"?k(M/C[I]):this.$d[I],M||0},b.add=function(d,M,I){var f;return f=M?d*C[z(M)]:W(d)?d.$ms:R(d,this).$ms,R(this.$ms+f*(I?-1:1),this)},b.subtract=function(d,M){return this.add(d,M,!0)},b.locale=function(d){var M=this.clone();return M.$l=d,M},b.clone=function(){return R(this.$ms,this)},b.humanize=function(d){return t().add(this.$ms,"ms").locale(this.$l).fromNow(!d)},b.valueOf=function(){return this.asMilliseconds()},b.milliseconds=function(){return this.get("milliseconds")},b.asMilliseconds=function(){return this.as("milliseconds")},b.seconds=function(){return this.get("seconds")},b.asSeconds=function(){return this.as("seconds")},b.minutes=function(){return this.get("minutes")},b.asMinutes=function(){return this.as("minutes")},b.hours=function(){return this.get("hours")},b.asHours=function(){return this.as("hours")},b.days=function(){return this.get("days")},b.asDays=function(){return this.as("days")},b.weeks=function(){return this.get("weeks")},b.asWeeks=function(){return this.as("weeks")},b.months=function(){return this.get("months")},b.asMonths=function(){return this.as("months")},b.years=function(){return this.get("years")},b.asYears=function(){return this.as("years")},E})(),X=o(function(E,b,d){return E.add(b.years()*d,"y").add(b.months()*d,"M").add(b.days()*d,"d").add(b.hours()*d,"h").add(b.minutes()*d,"m").add(b.seconds()*d,"s").add(b.milliseconds()*d,"ms")},"p");return function(E,b,d){t=d,n=d().$utils(),d.duration=function(f,y){var p=d.locale();return R(f,{$l:p},y)},d.isDuration=W;var M=b.prototype.add,I=b.prototype.subtract;b.prototype.add=function(f,y){return W(f)?X(this,f,1):M.bind(this)(f,y)},b.prototype.subtract=function(f,y){return W(f)?X(this,f,-1):I.bind(this)(f,y)}}}))});var Ft=(function(){var t=o(function(g,c,l,h){for(l=l||{},h=g.length;h--;l[g[h]]=c);return l},"o"),n=[6,8,10,12,13,14,15,16,17,18,20,21,22,23,24,25,26,27,28,29,30,31,33,35,36,38,40],r=[1,26],a=[1,27],s=[1,28],u=[1,29],x=[1,30],D=[1,31],S=[1,32],P=[1,33],C=[1,34],W=[1,9],R=[1,10],z=[1,11],v=[1,12],k=[1,13],O=[1,14],L=[1,15],G=[1,16],X=[1,19],E=[1,20],b=[1,21],d=[1,22],M=[1,23],I=[1,25],f=[1,35],y={trace:o(function(){},"trace"),yy:{},symbols_:{error:2,start:3,gantt:4,document:5,EOF:6,line:7,SPACE:8,statement:9,NL:10,weekday:11,weekday_monday:12,weekday_tuesday:13,weekday_wednesday:14,weekday_thursday:15,weekday_friday:16,weekday_saturday:17,weekday_sunday:18,weekend:19,weekend_friday:20,weekend_saturday:21,dateFormat:22,inclusiveEndDates:23,topAxis:24,axisFormat:25,tickInterval:26,excludes:27,includes:28,todayMarker:29,title:30,acc_title:31,acc_title_value:32,acc_descr:33,acc_descr_value:34,acc_descr_multiline_value:35,section:36,clickStatement:37,taskTxt:38,taskData:39,click:40,callbackname:41,callbackargs:42,href:43,clickStatementDebug:44,$accept:0,$end:1},terminals_:{2:"error",4:"gantt",6:"EOF",8:"SPACE",10:"NL",12:"weekday_monday",13:"weekday_tuesday",14:"weekday_wednesday",15:"weekday_thursday",16:"weekday_friday",17:"weekday_saturday",18:"weekday_sunday",20:"weekend_friday",21:"weekend_saturday",22:"dateFormat",23:"inclusiveEndDates",24:"topAxis",25:"axisFormat",26:"tickInterval",27:"excludes",28:"includes",29:"todayMarker",30:"title",31:"acc_title",32:"acc_title_value",33:"acc_descr",34:"acc_descr_value",35:"acc_descr_multiline_value",36:"section",38:"taskTxt",39:"taskData",40:"click",41:"callbackname",42:"callbackargs",43:"href"},productions_:[0,[3,3],[5,0],[5,2],[7,2],[7,1],[7,1],[7,1],[11,1],[11,1],[11,1],[11,1],[11,1],[11,1],[11,1],[19,1],[19,1],[9,1],[9,1],[9,1],[9,1],[9,1],[9,1],[9,1],[9,1],[9,1],[9,1],[9,1],[9,2],[9,2],[9,1],[9,1],[9,1],[9,2],[37,2],[37,3],[37,3],[37,4],[37,3],[37,4],[37,2],[44,2],[44,3],[44,3],[44,4],[44,3],[44,4],[44,2]],performAction:o(function(c,l,h,m,w,i,F){var e=i.length-1;switch(w){case 1:return i[e-1];case 2:this.$=[];break;case 3:i[e-1].push(i[e]),this.$=i[e-1];break;case 4:case 5:this.$=i[e];break;case 6:case 7:this.$=[];break;case 8:m.setWeekday("monday");break;case 9:m.setWeekday("tuesday");break;case 10:m.setWeekday("wednesday");break;case 11:m.setWeekday("thursday");break;case 12:m.setWeekday("friday");break;case 13:m.setWeekday("saturday");break;case 14:m.setWeekday("sunday");break;case 15:m.setWeekend("friday");break;case 16:m.setWeekend("saturday");break;case 17:m.setDateFormat(i[e].substr(11)),this.$=i[e].substr(11);break;case 18:m.enableInclusiveEndDates(),this.$=i[e].substr(18);break;case 19:m.TopAxis(),this.$=i[e].substr(8);break;case 20:m.setAxisFormat(i[e].substr(11)),this.$=i[e].substr(11);break;case 21:m.setTickInterval(i[e].substr(13)),this.$=i[e].substr(13);break;case 22:m.setExcludes(i[e].substr(9)),this.$=i[e].substr(9);break;case 23:m.setIncludes(i[e].substr(9)),this.$=i[e].substr(9);break;case 24:m.setTodayMarker(i[e].substr(12)),this.$=i[e].substr(12);break;case 27:m.setDiagramTitle(i[e].substr(6)),this.$=i[e].substr(6);break;case 28:this.$=i[e].trim(),m.setAccTitle(this.$);break;case 29:case 30:this.$=i[e].trim(),m.setAccDescription(this.$);break;case 31:m.addSection(i[e].substr(8)),this.$=i[e].substr(8);break;case 33:m.addTask(i[e-1],i[e]),this.$="task";break;case 34:this.$=i[e-1],m.setClickEvent(i[e-1],i[e],null);break;case 35:this.$=i[e-2],m.setClickEvent(i[e-2],i[e-1],i[e]);break;case 36:this.$=i[e-2],m.setClickEvent(i[e-2],i[e-1],null),m.setLink(i[e-2],i[e]);break;case 37:this.$=i[e-3],m.setClickEvent(i[e-3],i[e-2],i[e-1]),m.setLink(i[e-3],i[e]);break;case 38:this.$=i[e-2],m.setClickEvent(i[e-2],i[e],null),m.setLink(i[e-2],i[e-1]);break;case 39:this.$=i[e-3],m.setClickEvent(i[e-3],i[e-1],i[e]),m.setLink(i[e-3],i[e-2]);break;case 40:this.$=i[e-1],m.setLink(i[e-1],i[e]);break;case 41:case 47:this.$=i[e-1]+" "+i[e];break;case 42:case 43:case 45:this.$=i[e-2]+" "+i[e-1]+" "+i[e];break;case 44:case 46:this.$=i[e-3]+" "+i[e-2]+" "+i[e-1]+" "+i[e];break}},"anonymous"),table:[{3:1,4:[1,2]},{1:[3]},t(n,[2,2],{5:3}),{6:[1,4],7:5,8:[1,6],9:7,10:[1,8],11:17,12:r,13:a,14:s,15:u,16:x,17:D,18:S,19:18,20:P,21:C,22:W,23:R,24:z,25:v,26:k,27:O,28:L,29:G,30:X,31:E,33:b,35:d,36:M,37:24,38:I,40:f},t(n,[2,7],{1:[2,1]}),t(n,[2,3]),{9:36,11:17,12:r,13:a,14:s,15:u,16:x,17:D,18:S,19:18,20:P,21:C,22:W,23:R,24:z,25:v,26:k,27:O,28:L,29:G,30:X,31:E,33:b,35:d,36:M,37:24,38:I,40:f},t(n,[2,5]),t(n,[2,6]),t(n,[2,17]),t(n,[2,18]),t(n,[2,19]),t(n,[2,20]),t(n,[2,21]),t(n,[2,22]),t(n,[2,23]),t(n,[2,24]),t(n,[2,25]),t(n,[2,26]),t(n,[2,27]),{32:[1,37]},{34:[1,38]},t(n,[2,30]),t(n,[2,31]),t(n,[2,32]),{39:[1,39]},t(n,[2,8]),t(n,[2,9]),t(n,[2,10]),t(n,[2,11]),t(n,[2,12]),t(n,[2,13]),t(n,[2,14]),t(n,[2,15]),t(n,[2,16]),{41:[1,40],43:[1,41]},t(n,[2,4]),t(n,[2,28]),t(n,[2,29]),t(n,[2,33]),t(n,[2,34],{42:[1,42],43:[1,43]}),t(n,[2,40],{41:[1,44]}),t(n,[2,35],{43:[1,45]}),t(n,[2,36]),t(n,[2,38],{42:[1,46]}),t(n,[2,37]),t(n,[2,39])],defaultActions:{},parseError:o(function(c,l){if(l.recoverable)this.trace(c);else{var h=new Error(c);throw h.hash=l,h}},"parseError"),parse:o(function(c){var l=this,h=[0],m=[],w=[null],i=[],F=this.table,e="",_=0,A=0,Y=0,$=2,H=1,V=i.slice.call(arguments,1),N=Object.create(this.lexer),U={yy:{}};for(var st in this.yy)Object.prototype.hasOwnProperty.call(this.yy,st)&&(U.yy[st]=this.yy[st]);N.setInput(c,U.yy),U.yy.lexer=N,U.yy.parser=this,typeof N.yylloc>"u"&&(N.yylloc={});var rt=N.yylloc;i.push(rt);var lt=N.options&&N.options.ranges;typeof U.yy.parseError=="function"?this.parseError=U.yy.parseError:this.parseError=Object.getPrototypeOf(this).parseError;function mt(q){h.length=h.length-2*q,w.length=w.length-q,i.length=i.length-q}o(mt,"popStack");function kt(){var q;return q=m.pop()||N.lex()||H,typeof q!="number"&&(q instanceof Array&&(m=q,q=m.pop()),q=l.symbols_[q]||q),q}o(kt,"lex");for(var j,at,K,Z,zi,Ct,ut={},bt,tt,se,Tt;;){if(K=h[h.length-1],this.defaultActions[K]?Z=this.defaultActions[K]:((j===null||typeof j>"u")&&(j=kt()),Z=F[K]&&F[K][j]),typeof Z>"u"||!Z.length||!Z[0]){var Mt="";Tt=[];for(bt in F[K])this.terminals_[bt]&&bt>$&&Tt.push("'"+this.terminals_[bt]+"'");N.showPosition?Mt="Parse error on line "+(_+1)+`:
`+N.showPosition()+`
Expecting `+Tt.join(", ")+", got '"+(this.terminals_[j]||j)+"'":Mt="Parse error on line "+(_+1)+": Unexpected "+(j==H?"end of input":"'"+(this.terminals_[j]||j)+"'"),this.parseError(Mt,{text:N.match,token:this.terminals_[j]||j,line:N.yylineno,loc:rt,expected:Tt})}if(Z[0]instanceof Array&&Z.length>1)throw new Error("Parse Error: multiple actions possible at state: "+K+", token: "+j);switch(Z[0]){case 1:h.push(j),w.push(N.yytext),i.push(N.yylloc),h.push(Z[1]),j=null,at?(j=at,at=null):(A=N.yyleng,e=N.yytext,_=N.yylineno,rt=N.yylloc,Y>0&&Y--);break;case 2:if(tt=this.productions_[Z[1]][1],ut.$=w[w.length-tt],ut._$={first_line:i[i.length-(tt||1)].first_line,last_line:i[i.length-1].last_line,first_column:i[i.length-(tt||1)].first_column,last_column:i[i.length-1].last_column},lt&&(ut._$.range=[i[i.length-(tt||1)].range[0],i[i.length-1].range[1]]),Ct=this.performAction.apply(ut,[e,A,_,U.yy,Z[1],w,i].concat(V)),typeof Ct<"u")return Ct;tt&&(h=h.slice(0,-1*tt*2),w=w.slice(0,-1*tt),i=i.slice(0,-1*tt)),h.push(this.productions_[Z[1]][0]),w.push(ut.$),i.push(ut._$),se=F[h[h.length-2]][h[h.length-1]],h.push(se);break;case 3:return!0}}return!0},"parse")},p=(function(){var g={EOF:1,parseError:o(function(l,h){if(this.yy.parser)this.yy.parser.parseError(l,h);else throw new Error(l)},"parseError"),setInput:o(function(c,l){return this.yy=l||this.yy||{},this._input=c,this._more=this._backtrack=this.done=!1,this.yylineno=this.yyleng=0,this.yytext=this.matched=this.match="",this.conditionStack=["INITIAL"],this.yylloc={first_line:1,first_column:0,last_line:1,last_column:0},this.options.ranges&&(this.yylloc.range=[0,0]),this.offset=0,this},"setInput"),input:o(function(){var c=this._input[0];this.yytext+=c,this.yyleng++,this.offset++,this.match+=c,this.matched+=c;var l=c.match(/(?:\r\n?|\n).*/g);return l?(this.yylineno++,this.yylloc.last_line++):this.yylloc.last_column++,this.options.ranges&&this.yylloc.range[1]++,this._input=this._input.slice(1),c},"input"),unput:o(function(c){var l=c.length,h=c.split(/(?:\r\n?|\n)/g);this._input=c+this._input,this.yytext=this.yytext.substr(0,this.yytext.length-l),this.offset-=l;var m=this.match.split(/(?:\r\n?|\n)/g);this.match=this.match.substr(0,this.match.length-1),this.matched=this.matched.substr(0,this.matched.length-1),h.length-1&&(this.yylineno-=h.length-1);var w=this.yylloc.range;return this.yylloc={first_line:this.yylloc.first_line,last_line:this.yylineno+1,first_column:this.yylloc.first_column,last_column:h?(h.length===m.length?this.yylloc.first_column:0)+m[m.length-h.length].length-h[0].length:this.yylloc.first_column-l},this.options.ranges&&(this.yylloc.range=[w[0],w[0]+this.yyleng-l]),this.yyleng=this.yytext.length,this},"unput"),more:o(function(){return this._more=!0,this},"more"),reject:o(function(){if(this.options.backtrack_lexer)this._backtrack=!0;else return this.parseError("Lexical error on line "+(this.yylineno+1)+`. You can only invoke reject() in the lexer when the lexer is of the backtracking persuasion (options.backtrack_lexer = true).
`+this.showPosition(),{text:"",token:null,line:this.yylineno});return this},"reject"),less:o(function(c){this.unput(this.match.slice(c))},"less"),pastInput:o(function(){var c=this.matched.substr(0,this.matched.length-this.match.length);return(c.length>20?"...":"")+c.substr(-20).replace(/\n/g,"")},"pastInput"),upcomingInput:o(function(){var c=this.match;return c.length<20&&(c+=this._input.substr(0,20-c.length)),(c.substr(0,20)+(c.length>20?"...":"")).replace(/\n/g,"")},"upcomingInput"),showPosition:o(function(){var c=this.pastInput(),l=new Array(c.length+1).join("-");return c+this.upcomingInput()+`
`+l+"^"},"showPosition"),test_match:o(function(c,l){var h,m,w;if(this.options.backtrack_lexer&&(w={yylineno:this.yylineno,yylloc:{first_line:this.yylloc.first_line,last_line:this.last_line,first_column:this.yylloc.first_column,last_column:this.yylloc.last_column},yytext:this.yytext,match:this.match,matches:this.matches,matched:this.matched,yyleng:this.yyleng,offset:this.offset,_more:this._more,_input:this._input,yy:this.yy,conditionStack:this.conditionStack.slice(0),done:this.done},this.options.ranges&&(w.yylloc.range=this.yylloc.range.slice(0))),m=c[0].match(/(?:\r\n?|\n).*/g),m&&(this.yylineno+=m.length),this.yylloc={first_line:this.yylloc.last_line,last_line:this.yylineno+1,first_column:this.yylloc.last_column,last_column:m?m[m.length-1].length-m[m.length-1].match(/\r?\n?/)[0].length:this.yylloc.last_column+c[0].length},this.yytext+=c[0],this.match+=c[0],this.matches=c,this.yyleng=this.yytext.length,this.options.ranges&&(this.yylloc.range=[this.offset,this.offset+=this.yyleng]),this._more=!1,this._backtrack=!1,this._input=this._input.slice(c[0].length),this.matched+=c[0],h=this.performAction.call(this,this.yy,this,l,this.conditionStack[this.conditionStack.length-1]),this.done&&this._input&&(this.done=!1),h)return h;if(this._backtrack){for(var i in w)this[i]=w[i];return!1}return!1},"test_match"),next:o(function(){if(this.done)return this.EOF;this._input||(this.done=!0);var c,l,h,m;this._more||(this.yytext="",this.match="");for(var w=this._currentRules(),i=0;i<w.length;i++)if(h=this._input.match(this.rules[w[i]]),h&&(!l||h[0].length>l[0].length)){if(l=h,m=i,this.options.backtrack_lexer){if(c=this.test_match(h,w[i]),c!==!1)return c;if(this._backtrack){l=!1;continue}else return!1}else if(!this.options.flex)break}return l?(c=this.test_match(l,w[m]),c!==!1?c:!1):this._input===""?this.EOF:this.parseError("Lexical error on line "+(this.yylineno+1)+`. Unrecognized text.
`+this.showPosition(),{text:"",token:null,line:this.yylineno})},"next"),lex:o(function(){var l=this.next();return l||this.lex()},"lex"),begin:o(function(l){this.conditionStack.push(l)},"begin"),popState:o(function(){var l=this.conditionStack.length-1;return l>0?this.conditionStack.pop():this.conditionStack[0]},"popState"),_currentRules:o(function(){return this.conditionStack.length&&this.conditionStack[this.conditionStack.length-1]?this.conditions[this.conditionStack[this.conditionStack.length-1]].rules:this.conditions.INITIAL.rules},"_currentRules"),topState:o(function(l){return l=this.conditionStack.length-1-Math.abs(l||0),l>=0?this.conditionStack[l]:"INITIAL"},"topState"),pushState:o(function(l){this.begin(l)},"pushState"),stateStackSize:o(function(){return this.conditionStack.length},"stateStackSize"),options:{"case-insensitive":!0},performAction:o(function(l,h,m,w){var i=w;switch(m){case 0:return this.begin("open_directive"),"open_directive";break;case 1:return this.begin("acc_title"),31;break;case 2:return this.popState(),"acc_title_value";break;case 3:return this.begin("acc_descr"),33;break;case 4:return this.popState(),"acc_descr_value";break;case 5:this.begin("acc_descr_multiline");break;case 6:this.popState();break;case 7:return"acc_descr_multiline_value";case 8:break;case 9:break;case 10:break;case 11:return 10;case 12:break;case 13:break;case 14:this.begin("href");break;case 15:this.popState();break;case 16:return 43;case 17:this.begin("callbackname");break;case 18:this.popState();break;case 19:this.popState(),this.begin("callbackargs");break;case 20:return 41;case 21:this.popState();break;case 22:return 42;case 23:this.begin("click");break;case 24:this.popState();break;case 25:return 40;case 26:return 4;case 27:return 22;case 28:return 23;case 29:return 24;case 30:return 25;case 31:return 26;case 32:return 28;case 33:return 27;case 34:return 29;case 35:return 12;case 36:return 13;case 37:return 14;case 38:return 15;case 39:return 16;case 40:return 17;case 41:return 18;case 42:return 20;case 43:return 21;case 44:return"date";case 45:return 30;case 46:return"accDescription";case 47:return 36;case 48:return 38;case 49:return 39;case 50:return":";case 51:return 6;case 52:return"INVALID"}},"anonymous"),rules:[/^(?:%%\{)/i,/^(?:accTitle\s*:\s*)/i,/^(?:(?!\n||)*[^\n]*)/i,/^(?:accDescr\s*:\s*)/i,/^(?:(?!\n||)*[^\n]*)/i,/^(?:accDescr\s*\{\s*)/i,/^(?:[\}])/i,/^(?:[^\}]*)/i,/^(?:%%(?!\{)*[^\n]*)/i,/^(?:[^\}]%%*[^\n]*)/i,/^(?:%%*[^\n]*[\n]*)/i,/^(?:[\n]+)/i,/^(?:\s+)/i,/^(?:%[^\n]*)/i,/^(?:href[\s]+["])/i,/^(?:["])/i,/^(?:[^"]*)/i,/^(?:call[\s]+)/i,/^(?:\([\s]*\))/i,/^(?:\()/i,/^(?:[^(]*)/i,/^(?:\))/i,/^(?:[^)]*)/i,/^(?:click[\s]+)/i,/^(?:[\s\n])/i,/^(?:[^\s\n]*)/i,/^(?:gantt\b)/i,/^(?:dateFormat\s[^#\n;]+)/i,/^(?:inclusiveEndDates\b)/i,/^(?:topAxis\b)/i,/^(?:axisFormat\s[^#\n;]+)/i,/^(?:tickInterval\s[^#\n;]+)/i,/^(?:includes\s[^#\n;]+)/i,/^(?:excludes\s[^#\n;]+)/i,/^(?:todayMarker\s[^\n;]+)/i,/^(?:weekday\s+monday\b)/i,/^(?:weekday\s+tuesday\b)/i,/^(?:weekday\s+wednesday\b)/i,/^(?:weekday\s+thursday\b)/i,/^(?:weekday\s+friday\b)/i,/^(?:weekday\s+saturday\b)/i,/^(?:weekday\s+sunday\b)/i,/^(?:weekend\s+friday\b)/i,/^(?:weekend\s+saturday\b)/i,/^(?:\d\d\d\d-\d\d-\d\d\b)/i,/^(?:title\s[^\n]+)/i,/^(?:accDescription\s[^#\n;]+)/i,/^(?:section\s[^\n]+)/i,/^(?:[^:\n]+)/i,/^(?::[^#\n;]+)/i,/^(?::)/i,/^(?:$)/i,/^(?:.)/i],conditions:{acc_descr_multiline:{rules:[6,7],inclusive:!1},acc_descr:{rules:[4],inclusive:!1},acc_title:{rules:[2],inclusive:!1},callbackargs:{rules:[21,22],inclusive:!1},callbackname:{rules:[18,19,20],inclusive:!1},href:{rules:[15,16],inclusive:!1},click:{rules:[24,25],inclusive:!1},INITIAL:{rules:[0,1,3,5,8,9,10,11,12,13,14,17,23,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52],inclusive:!0}}};return g})();y.lexer=p;function T(){this.yy={}}return o(T,"Parser"),T.prototype=y,y.Parser=T,new T})();Ft.parser=Ft;var Ye=Ft;var Fe=ot(ei(),1),Q=ot(re(),1),We=ot($e(),1),Pe=ot(Le(),1),Ve=ot(Ie(),1);Q.default.extend(We.default);Q.default.extend(Pe.default);Q.default.extend(Ve.default);var Ae={friday:5,saturday:6},J="",Gt="",Xt,Ut="",pt=[],gt=[],Zt=new Map,qt=[],Dt=[],ft="",Qt="",Ne=["active","done","crit","milestone","vert"],Kt=[],xt=!1,Jt=!1,te="sunday",St="saturday",Ht=0,ii=o(function(){qt=[],Dt=[],ft="",Kt=[],wt=0,Bt=void 0,_t=void 0,B=[],J="",Gt="",Qt="",Xt=void 0,Ut="",pt=[],gt=[],xt=!1,Jt=!1,Ht=0,Zt=new Map,ce(),te="sunday",St="saturday"},"clear"),ni=o(function(t){Gt=t},"setAxisFormat"),si=o(function(){return Gt},"getAxisFormat"),ri=o(function(t){Xt=t},"setTickInterval"),ai=o(function(){return Xt},"getTickInterval"),oi=o(function(t){Ut=t},"setTodayMarker"),ci=o(function(){return Ut},"getTodayMarker"),li=o(function(t){J=t},"setDateFormat"),ui=o(function(){xt=!0},"enableInclusiveEndDates"),di=o(function(){return xt},"endDatesAreInclusive"),fi=o(function(){Jt=!0},"enableTopAxis"),hi=o(function(){return Jt},"topAxisEnabled"),mi=o(function(t){Qt=t},"setDisplayMode"),ki=o(function(){return Qt},"getDisplayMode"),yi=o(function(){return J},"getDateFormat"),pi=o(function(t){pt=t.toLowerCase().split(/[\s,]+/)},"setIncludes"),gi=o(function(){return pt},"getIncludes"),xi=o(function(t){gt=t.toLowerCase().split(/[\s,]+/)},"setExcludes"),bi=o(function(){return gt},"getExcludes"),Ti=o(function(){return Zt},"getLinks"),vi=o(function(t){ft=t,qt.push(t)},"addSection"),wi=o(function(){return qt},"getSections"),_i=o(function(){let t=Oe(),n=10,r=0;for(;!t&&r<n;)t=Oe(),r++;return Dt=B,Dt},"getTasks"),ze=o(function(t,n,r,a){let s=t.format(n.trim()),u=t.format("YYYY-MM-DD");return a.includes(s)||a.includes(u)?!1:r.includes("weekends")&&(t.isoWeekday()===Ae[St]||t.isoWeekday()===Ae[St]+1)||r.includes(t.format("dddd").toLowerCase())?!0:r.includes(s)||r.includes(u)},"isInvalidDate"),Di=o(function(t){te=t},"setWeekday"),Si=o(function(){return te},"getWeekday"),Ci=o(function(t){St=t},"setWeekend"),Re=o(function(t,n,r,a){if(!r.length||t.manualEndTime)return;let s;t.startTime instanceof Date?s=(0,Q.default)(t.startTime):s=(0,Q.default)(t.startTime,n,!0),s=s.add(1,"d");let u;t.endTime instanceof Date?u=(0,Q.default)(t.endTime):u=(0,Q.default)(t.endTime,n,!0);let[x,D]=Mi(s,u,n,r,a);t.endTime=x.toDate(),t.renderEndTime=D},"checkTaskDates"),Mi=o(function(t,n,r,a,s){let u=!1,x=null;for(;t<=n;)u||(x=n.toDate()),u=ze(t,r,a,s),u&&(n=n.add(1,"d")),t=t.add(1,"d");return[n,x]},"fixTaskDates"),jt=o(function(t,n,r){if(r=r.trim(),o(D=>{let S=D.trim();return S==="x"||S==="X"},"isTimestampFormat")(n)&&/^\d+$/.test(r))return new Date(Number(r));let u=/^after\s+(?<ids>[\d\w- ]+)/.exec(r);if(u!==null){let D=null;for(let P of u.groups.ids.split(" ")){let C=ct(P);C!==void 0&&(!D||C.endTime>D.endTime)&&(D=C)}if(D)return D.endTime;let S=new Date;return S.setHours(0,0,0,0),S}let x=(0,Q.default)(r,n.trim(),!0);if(x.isValid())return x.toDate();{et.debug("Invalid date:"+r),et.debug("With date format:"+n.trim());let D=new Date(r);if(D===void 0||isNaN(D.getTime())||D.getFullYear()<-1e4||D.getFullYear()>1e4)throw new Error("Invalid date:"+r);return D}},"getStartDate"),He=o(function(t){let n=/^(\d+(?:\.\d+)?)([Mdhmswy]|ms)$/.exec(t.trim());return n!==null?[Number.parseFloat(n[1]),n[2]]:[NaN,"ms"]},"parseDuration"),je=o(function(t,n,r,a=!1){r=r.trim();let u=/^until\s+(?<ids>[\d\w- ]+)/.exec(r);if(u!==null){let C=null;for(let R of u.groups.ids.split(" ")){let z=ct(R);z!==void 0&&(!C||z.startTime<C.startTime)&&(C=z)}if(C)return C.startTime;let W=new Date;return W.setHours(0,0,0,0),W}let x=(0,Q.default)(r,n.trim(),!0);if(x.isValid())return a&&(x=x.add(1,"d")),x.toDate();let D=(0,Q.default)(t),[S,P]=He(r);if(!Number.isNaN(S)){let C=D.add(S,P);C.isValid()&&(D=C)}return D.toDate()},"getEndDate"),wt=0,dt=o(function(t){return t===void 0?(wt=wt+1,"task"+wt):t},"parseId"),Ei=o(function(t,n){let r;n.substr(0,1)===":"?r=n.substr(1,n.length):r=n;let a=r.split(","),s={};Ze(a,s,Ne);for(let x=0;x<a.length;x++)a[x]=a[x].trim();let u="";switch(a.length){case 1:s.id=dt(),s.startTime=t.endTime,u=a[0];break;case 2:s.id=dt(),s.startTime=jt(void 0,J,a[0]),u=a[1];break;case 3:s.id=dt(a[0]),s.startTime=jt(void 0,J,a[1]),u=a[2];break;default:}return u&&(s.endTime=je(s.startTime,J,u,xt),s.manualEndTime=(0,Q.default)(u,"YYYY-MM-DD",!0).isValid(),Re(s,J,gt,pt)),s},"compileData"),Yi=o(function(t,n){let r;n.substr(0,1)===":"?r=n.substr(1,n.length):r=n;let a=r.split(","),s={};Ze(a,s,Ne);for(let u=0;u<a.length;u++)a[u]=a[u].trim();switch(a.length){case 1:s.id=dt(),s.startTime={type:"prevTaskEnd",id:t},s.endTime={data:a[0]};break;case 2:s.id=dt(),s.startTime={type:"getStartDate",startData:a[0]},s.endTime={data:a[1]};break;case 3:s.id=dt(a[0]),s.startTime={type:"getStartDate",startData:a[1]},s.endTime={data:a[2]};break;default:}return s},"parseData"),Bt,_t,B=[],Be={},$i=o(function(t,n){let r={section:ft,type:ft,processed:!1,manualEndTime:!1,renderEndTime:null,raw:{data:n},task:t,classes:[]},a=Yi(_t,n);r.raw.startTime=a.startTime,r.raw.endTime=a.endTime,r.id=a.id,r.prevTaskId=_t,r.active=a.active,r.done=a.done,r.crit=a.crit,r.milestone=a.milestone,r.vert=a.vert,r.order=Ht,Ht++;let s=B.push(r);_t=r.id,Be[r.id]=s-1},"addTask"),ct=o(function(t){let n=Be[t];return B[n]},"findTaskById"),Li=o(function(t,n){let r={section:ft,type:ft,description:t,task:t,classes:[]},a=Ei(Bt,n);r.startTime=a.startTime,r.endTime=a.endTime,r.id=a.id,r.active=a.active,r.done=a.done,r.crit=a.crit,r.milestone=a.milestone,r.vert=a.vert,Bt=r,Dt.push(r)},"addTaskOrg"),Oe=o(function(){let t=o(function(r){let a=B[r],s="";switch(B[r].raw.startTime.type){case"prevTaskEnd":{let u=ct(a.prevTaskId);a.startTime=u.endTime;break}case"getStartDate":s=jt(void 0,J,B[r].raw.startTime.startData),s&&(B[r].startTime=s);break}return B[r].startTime&&(B[r].endTime=je(B[r].startTime,J,B[r].raw.endTime.data,xt),B[r].endTime&&(B[r].processed=!0,B[r].manualEndTime=(0,Q.default)(B[r].raw.endTime.data,"YYYY-MM-DD",!0).isValid(),Re(B[r],J,gt,pt))),B[r].processed},"compileTask"),n=!0;for(let[r,a]of B.entries())t(r),n=n&&a.processed;return n},"compileTasks"),Ii=o(function(t,n){let r=n;nt().securityLevel!=="loose"&&(r=(0,Fe.sanitizeUrl)(n)),t.split(",").forEach(function(a){ct(a)!==void 0&&(Xe(a,()=>{window.open(r,"_self")}),Zt.set(a,r))}),Ge(t,"clickable")},"setLink"),Ge=o(function(t,n){t.split(",").forEach(function(r){let a=ct(r);a!==void 0&&a.classes.push(n)})},"setClass"),Ai=o(function(t,n,r){if(nt().securityLevel!=="loose"||n===void 0)return;let a=[];if(typeof r=="string"){a=r.split(/,(?=(?:(?:[^"]*"){2})*[^"]*$)/);for(let u=0;u<a.length;u++){let x=a[u].trim();x.startsWith('"')&&x.endsWith('"')&&(x=x.substr(1,x.length-2)),a[u]=x}}a.length===0&&a.push(t),ct(t)!==void 0&&Xe(t,()=>{Ee.runFunc(n,...a)})},"setClickFun"),Xe=o(function(t,n){Kt.push(function(){let r=document.querySelector(`[id="${t}"]`);r!==null&&r.addEventListener("click",function(){n()})},function(){let r=document.querySelector(`[id="${t}-text"]`);r!==null&&r.addEventListener("click",function(){n()})})},"pushFun"),Oi=o(function(t,n,r){t.split(",").forEach(function(a){Ai(a,n,r)}),Ge(t,"clickable")},"setClickEvent"),Fi=o(function(t){Kt.forEach(function(n){n(t)})},"bindFunctions"),Ue={getConfig:o(()=>nt().gantt,"getConfig"),clear:ii,setDateFormat:li,getDateFormat:yi,enableInclusiveEndDates:ui,endDatesAreInclusive:di,enableTopAxis:fi,topAxisEnabled:hi,setAxisFormat:ni,getAxisFormat:si,setTickInterval:ri,getTickInterval:ai,setTodayMarker:oi,getTodayMarker:ci,setAccTitle:le,getAccTitle:ue,setDiagramTitle:he,getDiagramTitle:me,setDisplayMode:mi,getDisplayMode:ki,setAccDescription:de,getAccDescription:fe,addSection:vi,getSections:wi,getTasks:_i,addTask:$i,findTaskById:ct,addTaskOrg:Li,setIncludes:pi,getIncludes:gi,setExcludes:xi,getExcludes:bi,setClickEvent:Oi,setLink:Ii,getLinks:Ti,bindFunctions:Fi,parseDuration:He,isInvalidDate:ze,setWeekday:Di,getWeekday:Si,setWeekend:Ci};function Ze(t,n,r){let a=!0;for(;a;)a=!1,r.forEach(function(s){let u="^\\s*"+s+"\\s*$",x=new RegExp(u);t[0].match(x)&&(n[s]=!0,t.shift(1),a=!0)})}o(Ze,"getTaskTags");var ht=ot(re(),1),Ke=ot(qe(),1);ht.default.extend(Ke.default);var Wi=o(function(){et.debug("Something is calling, setConf, remove the call")},"setConf"),Qe={monday:ve,tuesday:we,wednesday:_e,thursday:De,friday:Se,saturday:Ce,sunday:Te},Pi=o((t,n)=>{let r=[...t].map(()=>-1/0),a=[...t].sort((u,x)=>u.startTime-x.startTime||u.order-x.order),s=0;for(let u of a)for(let x=0;x<r.length;x++)if(u.startTime>=r[x]){r[x]=u.endTime,u.order=x+n,x>s&&(s=x);break}return s},"getMaxIntersections"),it,ne=1e4,Vi=o(function(t,n,r,a){let s=nt().gantt,u=nt().securityLevel,x;u==="sandbox"&&(x=yt("#i"+n));let D=u==="sandbox"?yt(x.nodes()[0].contentDocument.body):yt("body"),S=u==="sandbox"?x.nodes()[0].contentDocument:document,P=S.getElementById(n);it=P.parentElement.offsetWidth,it===void 0&&(it=1200),s.useWidth!==void 0&&(it=s.useWidth);let C=a.db.getTasks(),W=[];for(let f of C)W.push(f.type);W=I(W);let R={},z=2*s.topPadding;if(a.db.getDisplayMode()==="compact"||s.displayMode==="compact"){let f={};for(let p of C)f[p.section]===void 0?f[p.section]=[p]:f[p.section].push(p);let y=0;for(let p of Object.keys(f)){let T=Pi(f[p],y)+1;y+=T,z+=T*(s.barHeight+s.barGap),R[p]=T}}else{z+=C.length*(s.barHeight+s.barGap);for(let f of W)R[f]=C.filter(y=>y.type===f).length}P.setAttribute("viewBox","0 0 "+it+" "+z);let v=D.select(`[id="${n}"]`),k=Me().domain([ye(C,function(f){return f.startTime}),ke(C,function(f){return f.endTime})]).rangeRound([0,it-s.leftPadding-s.rightPadding]);function O(f,y){let p=f.startTime,T=y.startTime,g=0;return p>T?g=1:p<T&&(g=-1),g}o(O,"taskCompare"),C.sort(O),L(C,it,z),oe(v,z,it,s.useMaxWidth),v.append("text").text(a.db.getDiagramTitle()).attr("x",it/2).attr("y",s.titleTopMargin).attr("class","titleText");function L(f,y,p){let T=s.barHeight,g=T+s.barGap,c=s.topPadding,l=s.leftPadding,h=be().domain([0,W.length]).range(["#00B9FA","#F95002"]).interpolate(xe);X(g,c,l,y,p,f,a.db.getExcludes(),a.db.getIncludes()),b(l,c,y,p),G(f,g,c,l,T,h,y,p),d(g,c,l,T,h),M(l,c,y,p)}o(L,"makeGantt");function G(f,y,p,T,g,c,l){f.sort((e,_)=>e.vert===_.vert?0:e.vert?1:-1);let m=[...new Set(f.map(e=>e.order))].map(e=>f.find(_=>_.order===e));v.append("g").selectAll("rect").data(m).enter().append("rect").attr("x",0).attr("y",function(e,_){return _=e.order,_*y+p-2}).attr("width",function(){return l-s.rightPadding/2}).attr("height",y).attr("class",function(e){for(let[_,A]of W.entries())if(e.type===A)return"section section"+_%s.numberSectionStyles;return"section section0"}).enter();let w=v.append("g").selectAll("rect").data(f).enter(),i=a.db.getLinks();if(w.append("rect").attr("id",function(e){return e.id}).attr("rx",3).attr("ry",3).attr("x",function(e){return e.milestone?k(e.startTime)+T+.5*(k(e.endTime)-k(e.startTime))-.5*g:k(e.startTime)+T}).attr("y",function(e,_){return _=e.order,e.vert?s.gridLineStartPadding:_*y+p}).attr("width",function(e){return e.milestone?g:e.vert?.08*g:k(e.renderEndTime||e.endTime)-k(e.startTime)}).attr("height",function(e){return e.vert?C.length*(s.barHeight+s.barGap)+s.barHeight*2:g}).attr("transform-origin",function(e,_){return _=e.order,(k(e.startTime)+T+.5*(k(e.endTime)-k(e.startTime))).toString()+"px "+(_*y+p+.5*g).toString()+"px"}).attr("class",function(e){let _="task",A="";e.classes.length>0&&(A=e.classes.join(" "));let Y=0;for(let[H,V]of W.entries())e.type===V&&(Y=H%s.numberSectionStyles);let $="";return e.active?e.crit?$+=" activeCrit":$=" active":e.done?e.crit?$=" doneCrit":$=" done":e.crit&&($+=" crit"),$.length===0&&($=" task"),e.milestone&&($=" milestone "+$),e.vert&&($=" vert "+$),$+=Y,$+=" "+A,_+$}),w.append("text").attr("id",function(e){return e.id+"-text"}).text(function(e){return e.task}).attr("font-size",s.fontSize).attr("x",function(e){let _=k(e.startTime),A=k(e.renderEndTime||e.endTime);if(e.milestone&&(_+=.5*(k(e.endTime)-k(e.startTime))-.5*g,A=_+g),e.vert)return k(e.startTime)+T;let Y=this.getBBox().width;return Y>A-_?A+Y+1.5*s.leftPadding>l?_+T-5:A+T+5:(A-_)/2+_+T}).attr("y",function(e,_){return e.vert?s.gridLineStartPadding+C.length*(s.barHeight+s.barGap)+60:(_=e.order,_*y+s.barHeight/2+(s.fontSize/2-2)+p)}).attr("text-height",g).attr("class",function(e){let _=k(e.startTime),A=k(e.endTime);e.milestone&&(A=_+g);let Y=this.getBBox().width,$="";e.classes.length>0&&($=e.classes.join(" "));let H=0;for(let[N,U]of W.entries())e.type===U&&(H=N%s.numberSectionStyles);let V="";return e.active&&(e.crit?V="activeCritText"+H:V="activeText"+H),e.done?e.crit?V=V+" doneCritText"+H:V=V+" doneText"+H:e.crit&&(V=V+" critText"+H),e.milestone&&(V+=" milestoneText"),e.vert&&(V+=" vertText"),Y>A-_?A+Y+1.5*s.leftPadding>l?$+" taskTextOutsideLeft taskTextOutside"+H+" "+V:$+" taskTextOutsideRight taskTextOutside"+H+" "+V+" width-"+Y:$+" taskText taskText"+H+" "+V+" width-"+Y}),nt().securityLevel==="sandbox"){let e;e=yt("#i"+n);let _=e.nodes()[0].contentDocument;w.filter(function(A){return i.has(A.id)}).each(function(A){var Y=_.querySelector("#"+A.id),$=_.querySelector("#"+A.id+"-text");let H=Y.parentNode;var V=_.createElement("a");V.setAttribute("xlink:href",i.get(A.id)),V.setAttribute("target","_top"),H.appendChild(V),V.appendChild(Y),V.appendChild($)})}}o(G,"drawRects");function X(f,y,p,T,g,c,l,h){if(l.length===0&&h.length===0)return;let m,w;for(let{startTime:Y,endTime:$}of c)(m===void 0||Y<m)&&(m=Y),(w===void 0||$>w)&&(w=$);if(!m||!w)return;if((0,ht.default)(w).diff((0,ht.default)(m),"year")>5){et.warn("The difference between the min and max time is more than 5 years. This will cause performance issues. Skipping drawing exclude days.");return}let i=a.db.getDateFormat(),F=[],e=null,_=(0,ht.default)(m);for(;_.valueOf()<=w;)a.db.isInvalidDate(_,i,l,h)?e?e.end=_:e={start:_,end:_}:e&&(F.push(e),e=null),_=_.add(1,"d");v.append("g").selectAll("rect").data(F).enter().append("rect").attr("id",Y=>"exclude-"+Y.start.format("YYYY-MM-DD")).attr("x",Y=>k(Y.start.startOf("day"))+p).attr("y",s.gridLineStartPadding).attr("width",Y=>k(Y.end.endOf("day"))-k(Y.start.startOf("day"))).attr("height",g-y-s.gridLineStartPadding).attr("transform-origin",function(Y,$){return(k(Y.start)+p+.5*(k(Y.end)-k(Y.start))).toString()+"px "+($*f+.5*g).toString()+"px"}).attr("class","exclude-range")}o(X,"drawExcludeDays");function E(f,y,p,T){if(p<=0||f>y)return 1/0;let g=y-f,c=ht.default.duration({[T??"day"]:p}).asMilliseconds();return c<=0?1/0:Math.ceil(g/c)}o(E,"getEstimatedTickCount");function b(f,y,p,T){let g=a.db.getDateFormat(),c=a.db.getAxisFormat(),l;c?l=c:g==="D"?l="%d":l=s.axisFormat??"%Y-%m-%d";let h=ge(k).tickSize(-T+y+s.gridLineStartPadding).tickFormat(Ot(l)),w=/^([1-9]\d*)(millisecond|second|minute|hour|day|week|month)$/.exec(a.db.getTickInterval()||s.tickInterval);if(w!==null){let i=parseInt(w[1],10);if(isNaN(i)||i<=0)et.warn(`Invalid tick interval value: "${w[1]}". Skipping custom tick interval.`);else{let F=w[2],e=a.db.getWeekday()||s.weekday,_=k.domain(),A=_[0],Y=_[1],$=E(A,Y,i,F);if($>ne)et.warn(`The tick interval "${i}${F}" would generate ${$} ticks, which exceeds the maximum allowed (${ne}). This may indicate an invalid date or time range. Skipping custom tick interval.`);else switch(F){case"millisecond":h.ticks(Et.every(i));break;case"second":h.ticks(Yt.every(i));break;case"minute":h.ticks($t.every(i));break;case"hour":h.ticks(Lt.every(i));break;case"day":h.ticks(It.every(i));break;case"week":h.ticks(Qe[e].every(i));break;case"month":h.ticks(At.every(i));break}}}if(v.append("g").attr("class","grid").attr("transform","translate("+f+", "+(T-50)+")").call(h).selectAll("text").style("text-anchor","middle").attr("fill","#000").attr("stroke","none").attr("font-size",10).attr("dy","1em"),a.db.topAxisEnabled()||s.topAxis){let i=pe(k).tickSize(-T+y+s.gridLineStartPadding).tickFormat(Ot(l));if(w!==null){let F=parseInt(w[1],10);if(isNaN(F)||F<=0)et.warn(`Invalid tick interval value: "${w[1]}". Skipping custom tick interval.`);else{let e=w[2],_=a.db.getWeekday()||s.weekday,A=k.domain(),Y=A[0],$=A[1];if(E(Y,$,F,e)<=ne)switch(e){case"millisecond":i.ticks(Et.every(F));break;case"second":i.ticks(Yt.every(F));break;case"minute":i.ticks($t.every(F));break;case"hour":i.ticks(Lt.every(F));break;case"day":i.ticks(It.every(F));break;case"week":i.ticks(Qe[_].every(F));break;case"month":i.ticks(At.every(F));break}}}v.append("g").attr("class","grid").attr("transform","translate("+f+", "+y+")").call(i).selectAll("text").style("text-anchor","middle").attr("fill","#000").attr("stroke","none").attr("font-size",10)}}o(b,"makeGrid");function d(f,y){let p=0,T=Object.keys(R).map(g=>[g,R[g]]);v.append("g").selectAll("text").data(T).enter().append(function(g){let c=g[0].split(ae.lineBreakRegex),l=-(c.length-1)/2,h=S.createElementNS("http://www.w3.org/2000/svg","text");h.setAttribute("dy",l+"em");for(let[m,w]of c.entries()){let i=S.createElementNS("http://www.w3.org/2000/svg","tspan");i.setAttribute("alignment-baseline","central"),i.setAttribute("x","10"),m>0&&i.setAttribute("dy","1em"),i.textContent=w,h.appendChild(i)}return h}).attr("x",10).attr("y",function(g,c){if(c>0)for(let l=0;l<c;l++)return p+=T[c-1][1],g[1]*f/2+p*f+y;else return g[1]*f/2+y}).attr("font-size",s.sectionFontSize).attr("class",function(g){for(let[c,l]of W.entries())if(g[0]===l)return"sectionTitle sectionTitle"+c%s.numberSectionStyles;return"sectionTitle"})}o(d,"vertLabels");function M(f,y,p,T){let g=a.db.getTodayMarker();if(g==="off")return;let c=v.append("g").attr("class","today"),l=new Date,h=c.append("line");h.attr("x1",k(l)+f).attr("x2",k(l)+f).attr("y1",s.titleTopMargin).attr("y2",T-s.titleTopMargin).attr("class","today"),g!==""&&h.attr("style",g.replace(/,/g,";"))}o(M,"drawToday");function I(f){let y={},p=[];for(let T=0,g=f.length;T<g;++T)Object.prototype.hasOwnProperty.call(y,f[T])||(y[f[T]]=!0,p.push(f[T]));return p}o(I,"checkUnique")},"draw"),Je={setConf:Wi,draw:Vi};var Ni=o(t=>`
  .mermaid-main-font {
        font-family: ${t.fontFamily};
  }

  .exclude-range {
    fill: ${t.excludeBkgColor};
  }

  .section {
    stroke: none;
    opacity: 0.2;
  }

  .section0 {
    fill: ${t.sectionBkgColor};
  }

  .section2 {
    fill: ${t.sectionBkgColor2};
  }

  .section1,
  .section3 {
    fill: ${t.altSectionBkgColor};
    opacity: 0.2;
  }

  .sectionTitle0 {
    fill: ${t.titleColor};
  }

  .sectionTitle1 {
    fill: ${t.titleColor};
  }

  .sectionTitle2 {
    fill: ${t.titleColor};
  }

  .sectionTitle3 {
    fill: ${t.titleColor};
  }

  .sectionTitle {
    text-anchor: start;
    font-family: ${t.fontFamily};
  }


  /* Grid and axis */

  .grid .tick {
    stroke: ${t.gridColor};
    opacity: 0.8;
    shape-rendering: crispEdges;
  }

  .grid .tick text {
    font-family: ${t.fontFamily};
    fill: ${t.textColor};
  }

  .grid path {
    stroke-width: 0;
  }


  /* Today line */

  .today {
    fill: none;
    stroke: ${t.todayLineColor};
    stroke-width: 2px;
  }


  /* Task styling */

  /* Default task */

  .task {
    stroke-width: 2;
  }

  .taskText {
    text-anchor: middle;
    font-family: ${t.fontFamily};
  }

  .taskTextOutsideRight {
    fill: ${t.taskTextDarkColor};
    text-anchor: start;
    font-family: ${t.fontFamily};
  }

  .taskTextOutsideLeft {
    fill: ${t.taskTextDarkColor};
    text-anchor: end;
  }


  /* Special case clickable */

  .task.clickable {
    cursor: pointer;
  }

  .taskText.clickable {
    cursor: pointer;
    fill: ${t.taskTextClickableColor} !important;
    font-weight: bold;
  }

  .taskTextOutsideLeft.clickable {
    cursor: pointer;
    fill: ${t.taskTextClickableColor} !important;
    font-weight: bold;
  }

  .taskTextOutsideRight.clickable {
    cursor: pointer;
    fill: ${t.taskTextClickableColor} !important;
    font-weight: bold;
  }


  /* Specific task settings for the sections*/

  .taskText0,
  .taskText1,
  .taskText2,
  .taskText3 {
    fill: ${t.taskTextColor};
  }

  .task0,
  .task1,
  .task2,
  .task3 {
    fill: ${t.taskBkgColor};
    stroke: ${t.taskBorderColor};
  }

  .taskTextOutside0,
  .taskTextOutside2
  {
    fill: ${t.taskTextOutsideColor};
  }

  .taskTextOutside1,
  .taskTextOutside3 {
    fill: ${t.taskTextOutsideColor};
  }


  /* Active task */

  .active0,
  .active1,
  .active2,
  .active3 {
    fill: ${t.activeTaskBkgColor};
    stroke: ${t.activeTaskBorderColor};
  }

  .activeText0,
  .activeText1,
  .activeText2,
  .activeText3 {
    fill: ${t.taskTextDarkColor} !important;
  }


  /* Completed task */

  .done0,
  .done1,
  .done2,
  .done3 {
    stroke: ${t.doneTaskBorderColor};
    fill: ${t.doneTaskBkgColor};
    stroke-width: 2;
  }

  .doneText0,
  .doneText1,
  .doneText2,
  .doneText3 {
    fill: ${t.taskTextDarkColor} !important;
  }

  /* Done task text displayed outside the bar sits against the diagram background,
     not against the done-task bar, so it must use the outside/contrast color. */
  .doneText0.taskTextOutsideLeft,
  .doneText0.taskTextOutsideRight,
  .doneText1.taskTextOutsideLeft,
  .doneText1.taskTextOutsideRight,
  .doneText2.taskTextOutsideLeft,
  .doneText2.taskTextOutsideRight,
  .doneText3.taskTextOutsideLeft,
  .doneText3.taskTextOutsideRight {
    fill: ${t.taskTextOutsideColor} !important;
  }


  /* Tasks on the critical line */

  .crit0,
  .crit1,
  .crit2,
  .crit3 {
    stroke: ${t.critBorderColor};
    fill: ${t.critBkgColor};
    stroke-width: 2;
  }

  .activeCrit0,
  .activeCrit1,
  .activeCrit2,
  .activeCrit3 {
    stroke: ${t.critBorderColor};
    fill: ${t.activeTaskBkgColor};
    stroke-width: 2;
  }

  .doneCrit0,
  .doneCrit1,
  .doneCrit2,
  .doneCrit3 {
    stroke: ${t.critBorderColor};
    fill: ${t.doneTaskBkgColor};
    stroke-width: 2;
    cursor: pointer;
    shape-rendering: crispEdges;
  }

  .milestone {
    transform: rotate(45deg) scale(0.8,0.8);
  }

  .milestoneText {
    font-style: italic;
  }
  .doneCritText0,
  .doneCritText1,
  .doneCritText2,
  .doneCritText3 {
    fill: ${t.taskTextDarkColor} !important;
  }

  /* Done-crit task text outside the bar \u2014 same reasoning as doneText above. */
  .doneCritText0.taskTextOutsideLeft,
  .doneCritText0.taskTextOutsideRight,
  .doneCritText1.taskTextOutsideLeft,
  .doneCritText1.taskTextOutsideRight,
  .doneCritText2.taskTextOutsideLeft,
  .doneCritText2.taskTextOutsideRight,
  .doneCritText3.taskTextOutsideLeft,
  .doneCritText3.taskTextOutsideRight {
    fill: ${t.taskTextOutsideColor} !important;
  }

  .vert {
    stroke: ${t.vertLineColor};
  }

  .vertText {
    font-size: 15px;
    text-anchor: middle;
    fill: ${t.vertLineColor} !important;
  }

  .activeCritText0,
  .activeCritText1,
  .activeCritText2,
  .activeCritText3 {
    fill: ${t.taskTextDarkColor} !important;
  }

  .titleText {
    text-anchor: middle;
    font-size: 18px;
    fill: ${t.titleColor||t.textColor};
    font-family: ${t.fontFamily};
  }
`,"getStyles"),ti=Ni;var hn={parser:Ye,db:Ue,renderer:Je,styles:ti};export{hn as diagram};
