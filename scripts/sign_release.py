#!/usr/bin/env python3
"""Sign AX 8 PRO with the private identity supplied through one GitHub Secret."""
import base64, io, json, os, re, subprocess, tempfile, zipfile
from hashlib import sha256
from pathlib import Path

root=Path(__file__).resolve().parents[1]
secret=os.environ.get("AX8PRO_SIGNING_BUNDLE","").strip()
if not secret:
    raise SystemExit("Missing GitHub Actions Secret: AX8PRO_SIGNING_BUNDLE")
try:
    raw=base64.b64decode(secret,validate=True)
    if len(raw)>65536: raise ValueError("Bundle oversized")
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        config=json.loads(archive.read("signing.json"))
        key_bytes=archive.read("ax8pro-release.p12")
except Exception as exc:
    raise SystemExit("Invalid signing bundle: expected Base64 ZIP with signing.json and ax8pro-release.p12") from exc
sdk=Path(os.environ["ANDROID_SDK_ROOT"])
tools=sdk/"build-tools"/"36.0.0"
source_dir=root/"app"/"build"/"outputs"/"apk"/"release"
apks=sorted(source_dir.glob("*.apk"))
if len(apks)!=1: raise SystemExit("Expected one unsigned release APK, found "+str(len(apks)))
destination=root/"dist"/"AX8PRO.apk"
destination.parent.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix="ax8pro-identity-") as tmp:
    work=Path(tmp)
    key=work/"ax8pro-release.p12"
    key.write_bytes(key_bytes)
    key.chmod(0o600)
    cert=work/"release.cer"
    env=os.environ.copy()
    env["AX8PRO_KS_PASS"]=config["keystore_password"]
    env["AX8PRO_KEY_PASS"]=config["key_password"]
    subprocess.run(["keytool","-exportcert","-alias",config["alias"],
        "-keystore",str(key),"-storetype","PKCS12",
        "-storepass:env","AX8PRO_KS_PASS","-file",str(cert)],
        check=True,env=env,capture_output=True)
    actual=sha256(cert.read_bytes()).hexdigest().upper()
    if actual!=config["certificate_sha256"].upper():
        raise SystemExit("Signing identity does not match backup metadata")
    source=(root/"app/src/main/java/ru/xarkon/ax8/UpdateManager.java").read_text()
    expected=re.search(r'TRUSTED_CERT_SHA256="([0-9A-F]{64})"',source)
    if not expected or expected.group(1)!=actual:
        raise SystemExit("Signer certificate does not match the pinned official certificate")
    aligned=work/"aligned.apk"
    subprocess.run([str(tools/"zipalign"),"-f","-p","4",str(apks[0]),str(aligned)],check=True)
    subprocess.run([str(tools/"apksigner"),"sign",
        "--ks",str(key),"--ks-type","PKCS12",
        "--ks-key-alias",config["alias"],
        "--ks-pass","env:AX8PRO_KS_PASS",
        "--key-pass","env:AX8PRO_KEY_PASS",
        "--out",str(destination),str(aligned)],check=True,env=env)
    subprocess.run([str(tools/"apksigner"),"verify","--verbose","--print-certs",str(destination)],check=True)
    subprocess.run([str(tools/"zipalign"),"-c","-p","4",str(destination)],check=True)
print("AX 8 PRO signed using pinned certificate:",actual)
