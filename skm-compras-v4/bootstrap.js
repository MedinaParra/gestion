const fs=require('fs'),path=require('path'),zlib=require('zlib');
const raw=fs.readFileSync('payload.b64','utf8').trim();
const files=JSON.parse(zlib.gunzipSync(Buffer.from(raw,'base64')).toString('utf8'));
for(const [p,c] of Object.entries(files)){
  fs.mkdirSync(path.dirname(p),{recursive:true});
  fs.writeFileSync(p,c);
}
