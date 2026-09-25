# QuizMaster Web

Timed multiple-choice quizzes for a teacher and their students, in the browser.

This is a rebuild of the desktop [QuizMaster](https://github.com/aroojfatima-Ai-dev/quiz-master)
application (JavaFX + MySQL) as a **Java web application**: same features, same
design language, but a normal website you can open from any device and deploy to
[Railway](https://railway.app) with one container.

Built with Spring Boot 3.3 (Java 21), Thymeleaf, Spring Data JPA, and a plain
HTML/CSS/JS front end (no build step for the browser side).

---

## What it does

**Teacher**

* Create classes; each one gets a shareable **6-character code** (for example `PHY101`).
* Build a test with the two-step wizard — details first, then questions one by one.
* Or **import a questions file** (`.txt`, `.csv`, `.pdf`, `.docx`), preview every
  parsed question, edit or remove individual ones, and save all of them at once.
  Nothing is written until *Confirm & Save All*.
* Mark a test **public** (any registered student) or **class only**.
* Per-test expiry behaviour: `AUTO_SUBMIT` when the timer runs out, or
  `ALLOW_OVERTIME` where the extra time is recorded on the attempt.
* Review every submission with the score, the percentage and the per-question
  answer sheet.

**Student**

* Register, sign in, and join a class with its code.
* See the tests available to them (public ones plus their classes').
* Take a timed test with a **question palette**, previous/next navigation, and
  answers that survive a page refresh.
* Get a score immediately, with a per-question review of what was right.

**Both**

* Urdu (RTL) question authoring and display alongside English.
* Animated water-bubble background, responsive cards, one shared design system.
* Passwords hashed with PBKDF2-HMAC-SHA256 (210 000 iterations, per-user salt,
  constant-time comparison); older plaintext passwords are re-hashed on sign-in.
* The database schema is created automatically on first start, and a fresh
  install seeds a small demo world (see below).

---

## Run it locally

You need **JDK 21** and Maven.

```bash
mvn -B package
java -jar target/quizmaster-web.jar
```

Open <http://localhost:8080>. With no database configured the app creates an H2
file database in `./data/quizmaster` and seeds the demo data:

| Role    | Username      | Password     |
| ------- | ------------- | ------------ |
| Teacher | `prof_smith`  | `teacher123` |
| Student | `ali_student` | `student123` |

There is a class `Physics 101` with the code **PHY101**, a public 5-question
`General Knowledge Quiz`, a class-only Urdu test, and one submission to look at.

Set `QUIZMASTER_DEMO_SEED=false` to start with an empty database instead.

---

## Deploy to Railway

The image is a two-stage Docker build (`Dockerfile`): Maven builds the fat jar,
then a JRE-only image runs it as an unprivileged user. `railway.json` points
Railway at that Dockerfile and at the `/healthz` probe.

1. **Put the project in a Git repository** and push it to GitHub/GitLab.

   ```bash
   git init && git add . && git commit -m "QuizMaster Web"
   git remote add origin <your-repo-url> && git push -u origin main
   ```

2. **Create the Railway project**: *New Project → Deploy from GitHub repo* and
   pick the repository. Railway detects `railway.json` / `Dockerfile` and builds
   automatically.

3. **Add a database**: in the project, *New → Database → Add PostgreSQL*.
   Railway injects `DATABASE_URL` into the app, which picks it up on the next
   deploy — no configuration on your side. (MySQL works the same way.)

4. **Set any variables you want** (all optional):
   `QUIZMASTER_DEMO_SEED=false` for an empty database,
   `QUIZMASTER_DDL_AUTO=update` (default) is already sensible.

5. **Open the generated domain** (Settings → Networking → *Generate Domain*).
   `https://<your-app>.up.railway.app/healthz` should answer
   `{"status":"UP", ...}`.

Prefer the CLI? `railway up` from this directory deploys the same way, and
`railway variables --set DATABASE_URL=...` works for a database hosted elsewhere.

> **About persistence.** With a Postgres/MySQL plugin the data lives in that
> database. Without one, the app uses a local H2 file under `QUIZMASTER_DATA_DIR`
> (`/app/data` in the image) — attach a Railway **volume** mounted at `/app/data`
> if you want that to survive redeploys. Railway containers are otherwise
> ephemeral.

---

## How the database connection is chosen

`com.quizmaster.db.DbEnvironmentPostProcessor` runs before Spring Boot's
auto-configuration and picks the first thing that is configured:

| Order | Variables                                                                   | Result                                   |
| ----- | --------------------------------------------------------------------------- | ---------------------------------------- |
| 1     | `SPRING_DATASOURCE_URL` (or `spring.datasource.url` anywhere)                | used as-is                               |
| 2     | `DATABASE_URL`, `POSTGRES_URL`, `POSTGRESQL_URL`, `MYSQL_URL`, `DB_URL`, `QUIZMASTER_DB_URL` | `postgres://…` / `mysql://…` converted to JDBC |
| 3     | `MYSQLHOST`/`MYSQLPORT`/`MYSQLUSER`/`MYSQLPASSWORD`/`MYSQLDATABASE`          | MySQL on port 3306                       |
| 4     | `PGHOST`/`PGPORT`/`PGUSER`/`PGPASSWORD`/`PGDATABASE`                         | Postgres on port 5432                    |
| 5     | nothing at all                                                               | embedded H2 file: `${QUIZMASTER_DATA_DIR:./data}/quizmaster` |

Credentials in a URL are percent-decoded, MySQL connections negotiate `utf8mb4`
(needed for Urdu text), and Postgres defaults to `sslmode=prefer`.

The line `[quizmaster] database -> jdbc:…` in the startup log tells you which
one was used.

---

## Configuration

| Variable                      | Default        | Meaning                                            |
| ----------------------------- | -------------- | -------------------------------------------------- |
| `PORT`                        | `8080`         | HTTP port (Railway injects this)                   |
| `QUIZMASTER_DEMO_SEED`        | `true`         | seed demo data when the database is empty          |
| `QUIZMASTER_DDL_AUTO`         | `update`       | Hibernate schema handling (`none` on a managed DB) |
| `QUIZMASTER_DATA_DIR`         | `./data`       | folder for the H2 fallback database                |
| `QUIZMASTER_THYMELEAF_CACHE`  | `true`         | set `false` while editing templates                |
| `SPRING_DATASOURCE_URL`       | –              | explicit JDBC URL, overrides all detection         |
| `SPRING_DATASOURCE_USERNAME`  | –              | username for an explicit URL                       |
| `SPRING_DATASOURCE_PASSWORD`  | –              | password for an explicit URL                       |

---

## Question file format

A sample file ships in `src/main/resources/samples/sample-questions.txt` and is
served to signed-in teachers from the import screen.

```text
Q: What is the capital of France?
A) Berlin
B) Paris
C) Madrid
D) Rome
Answer: B
Topic: Geography
Difficulty: EASY
```

Rules the reader follows:

* **Keywords are case-insensitive**: `Q:` / `Q1:` / `Question 2:` start a
  question; `Answer:` / `Ans:` set the answer; `Topic:` and `Difficulty:` are
  optional and default to `General` and `Medium`.
* **Option markers**: `A)` `A.` `A:` `(A)` — mixed styles are fine.
* **Wrapped lines** continue the field above them, which is what PDF and Word
  exports need.
* **A whole question on one line** is split automatically when all four option
  markers appear in order.
* **Text answers are matched to options** by their text (`Answer: Queue` finds
  option B); an ambiguous answer is skipped rather than guessed.
* **Difficulty synonyms** map to `EASY` / `MEDIUM` / `HARD`; anything else
  becomes `MEDIUM`.
* **A block is skipped, with its line number and a reason**, when the question
  text or an option is missing, an option exceeds 500 characters, or the answer
  cannot be read. Up to 200 questions per file; text before the first `Q:` is
  ignored.
* **Nothing is saved until you confirm** — the preview screen lets you edit or
  remove each parsed question first, and the whole test is saved in one
  transaction.

---

## Tests and smoke check

```bash
mvn test                       # 65 tests: 0 failures
mvn -B package                 # builds target/quizmaster-web.jar
```

The suite covers the login/session rules, password hashing, input validation,
the import parser (including the tolerance and layout cases), the environment
post-processor's URL resolution, the seeded world, and end-to-end flows through
MockMvc — signing in, creating a class and a test, importing a file, joining,
taking a test and reading the result.

`ScreenRenderTest` renders **every screen** of the app against the demo data, so
a broken template fails the build. The same walk can be run against a live
deployment:

```bash
python3 scripts/smoke.py https://your-app.up.railway.app
```

---

## Project layout

```text
src/main/java/com/quizmaster/
  db/          database resolution (Railway/Railway-MySQL/H2)
  model/       User, ClassRoom, Enrollment, Quiz, Question, Result
  repo/        Spring Data repositories
  service/     auth, classes, tests, import, attempts (grading)
  importing/   multi-format question-file parser
  security/    PBKDF2 password hashing
  validation/  input and question validation
  seed/        demo data for a fresh database
  web/         controllers, session auth, Thymeleaf views
src/main/resources/
  templates/   Thymeleaf screens (teacher/, student/, fragments/)
  static/      css/app.css design system, js/ (bubbles, timer, palette)
  samples/     sample-questions.txt
scripts/smoke.py   walks every screen of a running instance
Dockerfile         two-stage build for Railway
railway.json       build + /healthz health check
```

> **Maintainer note.** `DbEnvironmentPostProcessor` is registered in
> `src/main/resources/META-INF/spring.factories`. That is the file Spring Boot
> 3.3 reads for `EnvironmentPostProcessor` implementations
> (`SpringFactoriesLoader.forDefaultResourceLocation`); an
> `EnvironmentPostProcessor.imports` file is only consulted for
> auto-configurations and is silently ignored for this extension point.

---

## Credits

Feature set and visual design follow the original desktop
[quiz-master](https://github.com/aroojfatima-Ai-dev/quiz-master) by
aroojfatima-Ai-dev; this project re-implements it for the browser and for
container hosting.
