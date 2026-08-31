# popp-client — Debian installation

Debian package for `popp-client`, built with jdeb and targeting Debian 13
(trixie).

## Build

```bash
./mvnw clean install -Dmaven.test.skip=true
# → popp-client/target/popp-client_2.7.0_all.deb
```

Always build with `clean`. An incremental build (`-am` without `clean`) leaves
the jaxb2 stale-flag files in `api-telematik/target/jaxb2/` in place, XJC skips
regenerating the JAXB classes, and the resulting jar is missing classes such as
`de.gematik.ws.conn.servicedirectory.v3.ConnectorServices`. The build succeeds
and only fails at runtime with `NoClassDefFoundError`.

To build without producing a package, pass `-Dskip.debbuild=true`.

Record the hash of what you built — you will compare it on the target host:

```bash
sha256sum popp-client/target/popp-client_2.7.0_all.deb
```

Maven jars are not reproducible (timestamps differ per build), so two debs
built from identical sources have different hashes. Compare the file you
copied, not two independent builds.

## Install

```bash
scp popp-client/target/popp-client_2.7.0_all.deb user@HOST:/tmp/
ssh user@HOST
sudo apt install /tmp/popp-client_2.7.0_all.deb
```

Use `apt install` with a path (not `dpkg -i`) for the first install — apt
resolves `openjdk-21-jre-headless` from the archive, whereas `dpkg -i` would
leave the package unconfigured with an unmet dependency.

The postinst creates the `popp-client` system user, enables the unit, and
deliberately **does not start the service** — nothing is configured yet, so it
would only crash-loop.

## Configure

All settings live in `/etc/default/popp-client`, read by systemd as root before
it drops to the service user. Secrets in it are never exposed to that user.
It is a dpkg conffile, so your edits survive upgrades.

```bash
sudo cp your-konnektor.p12 /etc/popp-client/konnektor.p12
sudo editor /etc/default/popp-client
sudo systemctl restart popp-client
```

You do not need to set permissions on the `.p12`. The unit runs an
`ExecStartPre` as root that sets every `*.p12` in `/etc/popp-client/` to
`root:popp-client` mode `0640` before the JVM starts.

### JVM

| Variable | Default | Notes |
|---|---|---|
| `JAVA` | `/usr/bin/java` | Override to pin a specific JVM. |
| `JAVA_OPTS` | `-Xmx512m -Djava.util.prefs.userRoot=…` | See [ZETA storage](#zeta-storage-key) for why the prefs paths are pinned. |

### HTTP

| Variable | Default | Notes |
|---|---|---|
| `SERVER_PORT` | `8081` | |
| `SERVER_ADDRESS` | all interfaces | Set `127.0.0.1` to restrict to loopback. Does not affect CETP. |

### PoPP service

| Variable | Default |
|---|---|
| `POPP_SERVER_URL` | `wss://popp.dev.poppservice.de:443/popp/practitioner/api/v1/token-generation-ehc` |
| `FEDERATION_ENTITY_STATEMENT_URL` | `https://popp.dev.poppservice.de/.well-known/openid-federation` |

### Konnektor

| Variable | Default | Notes |
|---|---|---|
| `CONNECTOR_END_POINT_URL` | `https://127.0.0.1` | |
| `CONNECTOR_SECURE_ENABLE` | `true` | |
| `CONNECTOR_SECURE_HOSTNAME_VALIDATION` | `false` | |
| `CONNECTOR_SECURE_TRUST_ALL` | `true` | |
| `CONNECTOR_SECURE_KEYSTORE` | `/etc/popp-client/konnektor.p12` | Client certificate. |
| `CONNECTOR_SECURE_KEYSTORE_PASSWORD` | `changeit` | |
| `CONNECTOR_SECURE_TRUSTSTORE` | `truststore.p12` | Only used when `TRUST_ALL=false`. |
| `CONNECTOR_SECURE_TRUSTSTORE_PASSWORD` | `changeit` | |

### Konnektor context

| Variable | Default | Notes |
|---|---|---|
| `CONTEXT_MANDANT_ID` | `M1` | |
| `CONTEXT_CLIENT_SYSTEM_ID` | `C1` | |
| `CONTEXT_WORKPLACE_ID` | `W1` | |
| `CARD_TERMINAL_ID` | empty | Card terminal identifier. |
| `CARD_TERMINAL_SLOT` | empty | Slot number. |

### CETP card events

| Variable | Default | Notes |
|---|---|---|
| `CONNECTOR_CETP_ENABLED` | `false` | |
| `CONNECTOR_CETP_EVENT_TO_URL` | empty | Address of **this host** as the Konnektor reaches it. |
| `CONNECTOR_CETP_EVENT_TO_PORT` | `8585` | Always binds all interfaces; see [Package layout](#package-layout). |
| `CONNECTOR_CETP_TLS_ENABLED` | `true` | |
| `CONNECTOR_CETP_RESUBSCRIBE_INTERVAL_HOURS` | `6` | |

### ZETA

| Variable | Default | Notes |
|---|---|---|
| `ZETA_AUTHENTICATION_SMB_KEYFILE` | `docker/zeta/smcb-private/smcb_private.p12` | **Relative path** — set an absolute one under `/etc/popp-client/` if you use the P12 provider rather than the connector-based SMC-B. |
| `ZETA_AUTHENTICATION_SMB_ALIAS` | `alias` | |
| `ZETA_AUTHENTICATION_SMB_PASSWORD` | `00` | |
| `ZETA_STORAGE_AES_B64_KEY` | hardcoded sample key | See below. |
| `ZETA_CLIENT_DISABLE_SERVER_VALIDATION` | `true` | |

### VSDM 2.0

| Variable | Default | Notes |
|---|---|---|
| `VSDM2_CLIENT_URL` | empty | e.g. `http://localhost:8083`. Empty disables the VSDM 2.0 fetch. |

### ZETA storage key

`application.yaml` ships a hardcoded fallback key. Omitting the variable does
**not** give you in-memory storage — you get persistent storage encrypted with
a key that is published in a public sample repository. Either set your own:

```bash
openssl rand -base64 32
```

or set the variable to empty (`ZETA_STORAGE_AES_B64_KEY=`) to force
`InMemoryStorage`, at the cost of re-registering with ZETA on every restart.

Persistent storage is written through `java.util.prefs`, which is why
`JAVA_OPTS` pins `-Djava.util.prefs.userRoot=/var/lib/popp-client/.java-prefs`.
The service user has no home directory and the unit sets `ProtectHome=yes`, so
the default location would not be writable.

### Settings not in the env file

Any key in `application.yaml` can be set here even without an explicit
`${...}` placeholder — Spring Boot relaxed binding maps `popp-server.url` to
`POPP_SERVER_URL`, `card-reader.name` to `CARD_READER_NAME`, and
`logging.level.de.gematik` to `LOGGING_LEVEL_DE_GEMATIK`. Two worth knowing:

| Variable | Default | Notes |
|---|---|---|
| `POPP_CLIENT_TOKEN_WAIT_TIMEOUT_SECONDS` | `5` | Short for real Konnektor round-trips; 40 is a practical value. |
| `POPP_CLIENT_STATIC_QR_TID` | empty | Without it, `GET /qrcode/static` answers `400`. |

## Start

```bash
sudo systemctl start popp-client
systemctl status popp-client
sudo journalctl -u popp-client -f
curl -fsS http://localhost:8081/v3/api-docs -o /dev/null && echo UP
```

## Package layout

| Path | Notes |
|---|---|
| `/usr/share/popp-client/popp-client.jar` | Spring Boot fat jar |
| `/usr/lib/systemd/system/popp-client.service` | unit |
| `/etc/default/popp-client` | config, dpkg **conffile**, mode 0640 |
| `/etc/popp-client/` | keystores, `root:popp-client` mode 0750 |
| `/var/lib/popp-client/` | `StateDirectory`, working directory |

Ports: 8081 (HTTP), 8585 (CETP, inbound from the Konnektor). The CETP listener
always binds all interfaces and has no configuration knob for the bind address,
because the Konnektor must connect in to it. Restrict it with a firewall rule
scoped to the Konnektor:

```bash
sudo ufw allow from <konnektor-ip> to any port 8585 proto tcp
```

## Upgrade

```bash
scp popp-client/target/popp-client_2.7.0_all.deb user@HOST:/tmp/
sudo dpkg -i /tmp/popp-client_2.7.0_all.deb
sha256sum /usr/share/popp-client/popp-client.jar
```

Use `dpkg -i` when the version string is unchanged — `apt install` reports
"already the newest version" and does nothing, silently leaving the old jar in
place. Always confirm the jar hash changed before concluding a fix did not work.

Your configuration is preserved. `/etc/default/popp-client` is a conffile, so
dpkg keeps your edits; it only prompts if the packaged version changed too, and
keeping yours is the default (`dpkg -i --force-confold` never asks). Keystores
in `/etc/popp-client/`, drop-ins under
`/etc/systemd/system/popp-client.service.d/`, and `/var/lib/popp-client` are
not owned by the package and are never touched.

The postinst runs `try-restart`, which restarts a running service but will not
start a stopped or failed one — use `systemctl start` in that case.

## Remove

```bash
sudo apt remove popp-client     # keeps /etc and /var/lib
sudo apt purge popp-client      # also removes them
```

Purge does not delete the `popp-client` system user, per Debian convention.

## Troubleshooting

**Hangs after `Determined telematik id ... from SMC-B certificate`.**
ZETA authentication calls `externalAuthenticate`, which checks `PIN.SMC` and,
if it is not `VERIFIED`, triggers a Konnektor `VerifyPin` — that blocks until
someone enters the SMC-B PIN **on the card terminal's PIN pad**. Look for
`| PIN.SMC status is ..., requesting PIN verification`.

**No log output at all after a card event.** Check for
`| eGK inserted ..., starting PoPP token retrieval`. If it is absent, the block
is before the async handoff; if present but nothing follows, it is in ZETA.

**ZETA debug logging.** `zeta.http-log-level: ALL` is already set in
`application.yaml`; what is missing is the logback level. Add a drop-in rather
than editing the conffile:

```bash
sudo systemctl edit popp-client
```

```ini
[Service]
Environment=LOGGING_LEVEL_DE_GEMATIK_ZETA=DEBUG
Environment=LOGGING_LEVEL_IO_KTOR=DEBUG
```

Remove it later with `sudo systemctl revert popp-client`. Note that `ALL` logs
full request and response bodies, including SMC-B assertions and tokens.

**`NoClassDefFoundError` for a `de.gematik.ws.*` class.** The jar was built
incrementally. Rebuild with `clean` — see [Build](#build).
