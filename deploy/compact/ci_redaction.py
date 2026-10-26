"""Pure redaction for public CI diagnostics, including one-time UI receipts."""
import re

SENSITIVE_LINE = re.compile(
    r'password|secret|authorization|cookie|private.key|LoginFormDTO|临时密码|临时登录凭据|到期时间', re.I)


def redact(text, private=()):
    for value in sorted(set(private), key=len, reverse=True):
        if len(value) >= 8:
            text = text.replace(value, '[REDACTED]')
    text = re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', '[REDACTED JWT]', text)
    # Playwright can quote the complete visible credential dialog on assertion
    # failure. Those random receipts are intentionally absent from account files.
    return '\n'.join(line for line in text.splitlines() if not SENSITIVE_LINE.search(line))


def excerpt(text, private=(), limit=20000):
    # Scrub before truncation so the tail cannot remove a sensitive field label
    # while retaining the newly generated password that followed it.
    return redact(text, private)[-limit:]
