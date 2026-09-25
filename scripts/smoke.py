#!/usr/bin/env python3
"""Walks every QuizMaster Web screen against a running instance.

Usage:  python3 scripts/smoke.py [base-url]     (default http://localhost:8080)

Logs in as the demo teacher and the demo student and opens every page, driving
the two-step wizard far enough that the question and import screens render.
Fails loudly if a page returns an error status, renders a stack trace, or loses
an expected label. Useful right after a deploy ("did the whole app come up?"),
not as a replacement for the JUnit suite.
"""

import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import http.cookiejar

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080").rstrip("/")

ERROR_MARKERS = (
    "Whitelabel Error Page",
    "Internal Server Error",
    "TemplateProcessingException",
    "SpelEvaluationException",
    "java.lang.NullPointerException",
    "org.thymeleaf.exceptions",
)

results = []


def client():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def request(opener, path, data=None, accept="text/html"):
    body = urllib.parse.urlencode(data).encode() if data is not None else None
    headers = {"Accept": accept} if accept else {}
    call = urllib.request.Request(BASE + path, data=body, headers=headers)
    try:
        with opener.open(call, timeout=30) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace")


def get(opener, path, expect=None, label=None, accept="text/html", min_length=400):
    status, body = request(opener, path, accept=accept)
    return check(label or path, path, status, body, expect, min_length)


def post(opener, path, data, expect=None, label=None):
    status, body = request(opener, path, data=data)
    return check(label or path, path, status, body, expect)


def check(label, path, status, body, expect=None, min_length=400):
    problems = []
    if status >= 400:
        problems.append("HTTP " + str(status))
    for marker in ERROR_MARKERS:
        if marker in body:
            problems.append("rendered " + marker)
    if expect and expect.lower() not in body.lower():
        problems.append("missing %r" % expect)
    if len(body) < min_length:
        problems.append("suspiciously short body (%d bytes)" % len(body))
    results.append((label, path, status, problems))
    return not problems


def main():
    public = client()
    get(public, "/", "QuizMaster", label="welcome")
    get(public, "/login", "Sign in", label="login form")
    get(public, "/register", "Create", label="register form")
    get(public, "/healthz", "\"status\":\"UP\"", label="health check",
        accept="application/json", min_length=40)

    teacher = client()
    post(teacher, "/login", {"usernameOrEmail": "prof_smith", "password": "teacher123"},
         "Teacher: prof_smith", label="teacher login")
    get(teacher, "/teacher/dashboard", "Welcome, prof_smith", label="teacher dashboard")
    get(teacher, "/teacher/classes", "PHY101", label="classes")
    get(teacher, "/teacher/tests", "General Knowledge Quiz", label="tests")
    get(teacher, "/teacher/submissions", "ali_student", label="submissions")
    get(teacher, "/teacher/submissions/1", label="submission detail")

    # The wizard keeps its state in the session, so step 2 and the import screen
    # only render once step 1 has been submitted.
    get(teacher, "/teacher/tests/new", "Step 1 of 2", label="wizard step 1")
    post(teacher, "/teacher/tests/new",
         {"title": "Smoke Test", "language": "ENGLISH", "durationMinutes": "10",
          "expiryAction": "AUTO_SUBMIT", "visibility": "public"},
         "Step 2", label="wizard step 1 submit")
    get(teacher, "/teacher/tests/new/questions", "Add question", label="wizard step 2")
    post(teacher, "/teacher/tests/new/questions/submit",
         {"action": "add", "questionText": "Smoke question?", "optionA": "One",
          "optionB": "Two", "optionC": "Three", "optionD": "Four", "correctOption": "A"},
         "Smoke question?", label="wizard step 2 add question")
    get(teacher, "/teacher/tests/new/import", "Import", label="import screen")
    get(teacher, "/teacher/tests/new/import/sample", "Q:", label="sample file download",
        accept="text/plain", min_length=100)

    student = client()
    post(student, "/login", {"usernameOrEmail": "ali_student", "password": "student123"},
         "Welcome, ali_student", label="student login")
    get(student, "/student/dashboard", "Welcome, ali_student", label="student dashboard")
    get(student, "/student/join", "code", label="join class")
    get(student, "/student/tests", "General Knowledge Quiz", label="available tests")
    get(student, "/student/results", "General Knowledge Quiz", label="my results")
    get(student, "/student/results/1", "General Knowledge Quiz", label="result detail")

    status, body = request(student, "/student/tests")
    quiz_ids = re.findall(r"/student/tests/(\d+)/take", body)
    if quiz_ids:
        get(student, "/student/tests/%s/take" % quiz_ids[0], "Submit",
            label="taking a test")
    else:
        results.append(("taking a test", "/student/tests", 0, ["no test links found"]))

    width = max(len(label) for label, _, _, _ in results)
    failed = 0
    for label, path, status, problems in results:
        state = "ok  " if not problems else "FAIL"
        failed += 1 if problems else 0
        print("%s %-*s %-34s %s" % (state, width, label, path, "; ".join(problems)))
    print("\n%d/%d screens ok against %s" % (len(results) - failed, len(results), BASE))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
