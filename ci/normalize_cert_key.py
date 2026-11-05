#!/usr/bin/env python3
"""CERTIFICATE_PRIVATE_KEY 환경변수를 PEM 개인키 파일로 정리한다.

붙여넣기 과정에서 생기는 문제를 모두 받아준다:
- Windows 줄바꿈(CRLF), 줄바꿈이 공백이나 글자 \\n 으로 바뀐 경우
- 앞뒤 따옴표, BEGIN/END 줄이 빠지고 본문(MII...)만 들어간 경우
- 파일 전체를 base64로 한 번 더 인코딩한 경우(LS0tLS1CRUdJTi...)
키 내용은 절대 출력하지 않고, 실패하면 형식 정보만 보여준다.
사용: normalize_cert_key.py <출력파일>
"""
import base64, os, re, sys

out = sys.argv[1]
raw = os.environ.get("CERTIFICATE_PRIVATE_KEY", "")

def describe(s):
    s = s.strip()
    head = s[:4]
    safe = head if re.fullmatch(r"[-A-Za-z ]{1,4}|MII[A-Za-z]|LS0t|ssh-|PuTT", head or "x") else "?"
    return (f"길이 {len(s)}자, 줄 {s.count(chr(10)) + 1}개, 시작 '{safe}', "
            f"BEGIN 포함={'BEGIN' in s}, END 포함={'END' in s}")

def b64clean(s):
    return re.sub(r"[^A-Za-z0-9+/=]", "", s)

def pem(kind, body):
    body = b64clean(body)
    lines = [body[i:i + 64] for i in range(0, len(body), 64)]
    return f"-----BEGIN {kind}-----\n" + "\n".join(lines) + f"\n-----END {kind}-----\n"

s = raw.replace("\r", "").replace("\\n", "\n").strip().strip('"').strip("'").strip()

# base64로 한 번 더 감싼 경우 풀기
if "BEGIN" not in s and b64clean(s) == re.sub(r"\s", "", s):
    try:
        dec = base64.b64decode(b64clean(s) + "==", validate=False).decode("utf-8")
        if "BEGIN" in dec:
            s = dec.replace("\r", "").strip()
    except Exception:
        pass

result = None
m = re.search(r"-----BEGIN ([A-Z ]*PRIVATE KEY)-----(.*?)-----END \1-----", s, re.S)
if m:
    kind, body = m.group(1), m.group(2)
    if kind == "OPENSSH PRIVATE KEY":
        # ssh-keygen 으로 변환하도록 원래 형태 그대로 저장
        result = pem(kind, body)
    else:
        result = pem(kind, body)
elif "BEGIN" not in s and s.replace(" ", "").replace("\n", "").startswith("MII"):
    # 헤더 없이 본문만 들어온 경우: RSA(PKCS#1) 로 감싸고, 셸에서 openssl로 확인
    result = pem("RSA PRIVATE KEY", s)

if not result:
    print("CERTIFICATE_PRIVATE_KEY 값이 개인키 형식이 아닙니다. (" + describe(raw) + ")")
    if raw.strip().startswith("ssh-"):
        print("→ 공개키(cert_key.pub)가 들어갔습니다. .pub 이 아닌 cert_key 파일 내용을 넣어 주세요.")
    sys.exit(1)

with open(out, "w") as f:
    f.write(result)
os.chmod(out, 0o600)
print("키 형식 확인: " + describe(raw))
