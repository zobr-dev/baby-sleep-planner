# Планировщик детского сна — Kotlin + Spring Boot + PostgreSQL

Серверная версия приложения для расчёта времени укладывания ребёнка при графике
с дневными снами. Регистрация и авторизация, история расчётов по дням, средние
показатели бодрствования за 7 и 30 дней, авто-очистка записей старше 90 дней.

**Стек:** Kotlin · Spring Boot 3 (Web, Data JPA, Security, Validation) · PostgreSQL · Gradle (Kotlin DSL).

## Архитектура

- **Авторизация** — Spring Security + BCrypt для хеширования паролей, серверная
  сессия в httponly-cookie (`JSESSIONID`, `SameSite=Lax`). Идентификатор пользователя
  хранится в атрибуте сессии `uid`; контроллеры возвращают `401`, если он отсутствует.
- **Хранение** — PostgreSQL через Spring Data JPA. Сущности `users` и `history`,
  уникальность `history(user_id, date)` обеспечивает upsert «одна запись на день».
- **Фронтенд** — тот же интерфейс, что и в других версиях (`src/main/resources/static/index.html`),
  обращается к REST API; вся логика расчёта и таймлайн выполняются в браузере.

```
sleep-planner-kotlin/
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
├── docker-compose.yml
├── src/main/kotlin/com/sleepplanner/
│   ├── SleepPlannerApplication.kt      # точка входа
│   ├── config/SecurityConfig.kt        # Spring Security, BCrypt, сессии, CORS
│   ├── user/User.kt                    # сущность AppUser + репозиторий
│   ├── user/AuthController.kt          # /api/register, /login, /logout, /me
│   └── history/
│       ├── History.kt                  # сущность HistoryEntry + репозиторий
│       └── HistoryController.kt        # /api/history (CRUD) + очистка 90 дней
├── src/main/resources/
│   ├── application.yml                 # конфигурация БД и сессии
│   └── static/index.html               # фронтенд
└── src/test/kotlin/com/sleepplanner/
    └── ApiFlowTest.kt                  # интеграционные тесты (H2)
```

## Запуск через Docker Compose (проще всего)

Поднимает PostgreSQL и приложение одной командой:

```bash
cd sleep-planner-kotlin
docker compose up --build
```

Откройте http://localhost:8080

## Локальный запуск (без Docker)

Нужен JDK 17+ и работающий PostgreSQL.

1. Создайте базу и пользователя:

```sql
CREATE DATABASE sleepplanner;
CREATE USER sleep WITH PASSWORD 'sleep';
GRANT ALL PRIVILEGES ON DATABASE sleepplanner TO sleep;
```

2. Соберите wrapper (один раз, если в проекте нет `gradlew`) и запустите:

```bash
gradle wrapper            # создаст ./gradlew (нужен установленный Gradle 8+)
./gradlew bootRun
```

Если Gradle не установлен, используйте IntelliJ IDEA: «Open» → выберите папку проекта,
IDE сама подтянет зависимости и предложит запустить `SleepPlannerApplication`.

Приложение поднимется на http://localhost:8080 и подключится к БД по адресу из
`application.yml` (по умолчанию `localhost:5432/sleepplanner`, пользователь `sleep`).

## Конфигурация (переменные окружения)

| Переменная                 | Назначение                              | По умолчанию |
|----------------------------|-----------------------------------------|--------------|
| `JDBC_DATABASE_URL`        | JDBC-строка PostgreSQL                  | `jdbc:postgresql://localhost:5432/sleepplanner` |
| `JDBC_DATABASE_USERNAME`   | пользователь БД                         | `sleep`      |
| `JDBC_DATABASE_PASSWORD`   | пароль БД                               | `sleep`      |
| `JPA_DDL_AUTO`             | стратегия схемы (`update`/`validate`)   | `update`     |
| `SESSION_COOKIE_SECURE`    | флаг Secure для cookie (за HTTPS — `true`) | `false`   |
| `PORT`                     | порт приложения                         | `8080`       |
| `MAIL_ENABLED`             | включить реальную отправку писем (код сброса) | `false` |
| `MAIL_FROM`                | адрес отправителя писем                 | `no-reply@baby-sleep-planner.ru` |
| `SMTP_HOST`                | хост SMTP-сервера                       | _(пусто)_    |
| `SMTP_PORT`                | порт SMTP                               | `587`        |
| `SMTP_USERNAME`            | логин SMTP                              | _(пусто)_    |
| `SMTP_PASSWORD`            | пароль SMTP                             | _(пусто)_    |

> Если `MAIL_ENABLED=false` или SMTP не настроен, код сброса пароля не отправляется
> по почте, а выводится в лог приложения (удобно для локальной разработки).

## Тесты

```bash
./gradlew test
```

Интеграционные тесты (`ApiFlowTest`) поднимают контекст на in-memory H2 и проверяют
весь цикл: регистрация, сохранение/обновление записи, список в snake_case, удаление,
требование авторизации и отклонение неверного пароля.

## REST API

| Метод  | Путь                  | Назначение                          |
|--------|-----------------------|-------------------------------------|
| POST   | `/api/register`       | регистрация `{username, email, password}` |
| POST   | `/api/login`          | вход `{username, password}`         |
| POST   | `/api/logout`         | выход (инвалидация сессии)          |
| POST   | `/api/password/forgot`| запрос кода сброса `{email}`        |
| POST   | `/api/password/reset` | сброс пароля `{email, code, password}` |
| GET    | `/api/me`             | текущий пользователь                |
| GET    | `/api/history`        | список записей (+ очистка 90 дней)  |
| POST   | `/api/history`        | сохранить/обновить запись за дату   |
| DELETE | `/api/history/{date}` | удалить запись                      |

## Развёртывание на хостинге

- **Docker-платформы (Render, Railway, Fly.io)**: используйте `Dockerfile`; задайте
  переменные `JDBC_DATABASE_URL/USERNAME/PASSWORD` от managed-PostgreSQL и
  `SESSION_COOKIE_SECURE=true` (там HTTPS). Многие платформы дают `DATABASE_URL` —
  приведите его к JDBC-формату `jdbc:postgresql://host:5432/db`.
- **Свой сервер**: `./gradlew bootJar` → получите `build/libs/sleep-planner-1.0.0.jar`,
  запускайте `java -jar sleep-planner-1.0.0.jar` за nginx/HTTPS.
- На проде рекомендуется `JPA_DDL_AUTO=validate` и миграции схемы через Flyway/Liquibase
  вместо авто-`update`.

## Безопасность

- Пароли — только BCrypt-хеш, в открытом виде не хранятся.
- Сессия — httponly-cookie; за HTTPS включайте `SESSION_COOKIE_SECURE=true`.
- Все запросы к БД параметризованы через JPA (защита от SQL-инъекций).
- Аутентификация требуется для всех операций с историей.

## Примечание

Файлы Gradle Wrapper (`gradlew`, `gradle/wrapper/*`) не включены в архив. Сгенерируйте
их командой `gradle wrapper` или просто откройте проект в IntelliJ IDEA — это
стандартный Spring Boot Gradle-проект и собирается без дополнительной настройки.
