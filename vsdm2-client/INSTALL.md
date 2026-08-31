# vsdm2-client — Debian installation

Debian package for `vsdm2-client`, built with jdeb and targeting Debian 13
(trixie).

## Build

```bash
./mvnw clean install -Dmaven.test.skip=true
# → vsdm2-client/target/vsdm2-client_2.7.0_all.deb
```

**Always build with `clean`.** This module is the one that breaks otherwise.
An incremental build (`-am` without `clean`) leaves the jaxb2 stale-flag files
in `api-telematik/target/jaxb2/` in place, XJC skips regenerating the JAXB
classes, and the resulting jar is missing
`de.gematik.ws.conn.servicedirectory.v3.ConnectorServices`. The build succeeds;
the service then fails at startup with:

```
NoClassDefFoundError: de/gematik/ws/conn/servicedirectory/v3/ConnectorServices
    at ServicePortProvider.parse(ServicePortProvider.java:70)
```

To build without producing a package, pass `-Dskip.debbuild=true`.

### Verify before shipping

The failure above is invisible until runtime, so check the packaged jar:

```bash
python3 -c "import zipfile,io;z=zipfile.ZipFile('vsdm2-client/target/vsdm2-client-2.7.0.jar');n=[x for x in z.namelist() if 'api-telematik' in x];i=zipfile.ZipFile(io.BytesIO(z.read(n[0])));print([x for x in i.namelist() if 'ConnectorServices' in x])"
sha256sum vsdm2-client/target/vsdm2-client_2.7.0_all.deb
```

The first must print a non-empty list. Record the hash to compare on the target
host. Maven jars are not reproducible, so two builds from identical sources
differ — compare the file you copied, not two independent builds.

## Install

```bash
scp vsdm2-client/target/vsdm2-client_2.7.0_all.deb user@HOST:/tmp/
ssh user@HOST
sudo apt install /tmp/vsdm2-client_2.7.0_all.deb
```

Use `apt install` with a path (not `dpkg -i`) for the first install — apt
resolves `openjdk-21-jre-headless` from the archive.

The postinst creates the `vsdm2-client` system user, enables the unit, and
deliberately does not start the service.

## Configure

All settings live in `/etc/default/vsdm2-client`, read by systemd as root
before it drops to the service user. It is a dpkg conffile, so your edits
survive upgrades.

```bash
sudo cp your-konnektor.p12 /etc/vsdm2-client/konnektor.p12
sudo editor /etc/default/vsdm2-client
sudo systemctl restart vsdm2-client
```

The Konnektor keystore is normally the same one popp-client uses, but each
service needs its own copy — they run as different users and read from their
own `/etc` directory. You do not need to set permissions on it: the unit runs
an `ExecStartPre` as root that sets every `*.p12` in `/etc/vsdm2-client/` to
`root:vsdm2-client` mode `0640` before the JVM starts.

### JVM

| Variable | Default | Notes |
|---|---|---|
| `JAVA` | `/usr/bin/java` | Override to pin a specific JVM. |
| `JAVA_OPTS` | `-Xmx512m -Djavax.xml.accessExternalDTD=all` | **Do not drop the DTD flag** — see below. |

The Konnektor WSDLs import `xmldsig-core-schema.xsd`, whose DOCTYPE references
an external DTD. The JDK's secure-processing default blocks that and CXF fails
to build its ports. In development this flag comes from the
`spring-boot-maven-plugin` configuration, which does not apply to the packaged
jar.

### HTTP

| Variable | Default | Notes |
|---|---|---|
| `SERVER_PORT` | `8083` | |
| `SERVER_ADDRESS` | all interfaces | Set `127.0.0.1` to restrict to loopback — safe when only popp-client calls it. |

### Service discovery

| Variable | Default |
|---|---|
| `SERVICE_DISCOVERY_URL` | `https://service-discovery.dev.ti-platform.de/catalog.json` |
| `SERVICE_DISCOVERY_REFRESH_MINUTES` | `60` |

### Konnektor

| Variable | Default | Notes |
|---|---|---|
| `CONNECTOR_END_POINT_URL` | `https://127.0.0.1` | |
| `CONNECTOR_SECURE_ENABLE` | `true` | |
| `CONNECTOR_SECURE_HOSTNAME_VALIDATION` | `false` | |
| `CONNECTOR_SECURE_TRUST_ALL` | `true` | |
| `CONNECTOR_SECURE_KEYSTORE` | `/etc/vsdm2-client/konnektor.p12` | Client certificate. |
| `CONNECTOR_SECURE_KEYSTORE_PASSWORD` | `changeit` | |
| `CONNECTOR_SECURE_TRUSTSTORE` | `truststore.p12` | Only used when `TRUST_ALL=false`. |
| `CONNECTOR_SECURE_TRUSTSTORE_PASSWORD` | `changeit` | |

### Konnektor context

| Variable | Default | Notes |
|---|---|---|
| `CONTEXT_MANDANT_ID` | `M1` | |
| `CONTEXT_CLIENT_SYSTEM_ID` | `C1` | |
| `CONTEXT_WORKPLACE_ID` | `W1` | |
| `CONTEXT_USER_ID` | empty | |
| `CONTEXT_SMCB_ICCSN` | empty | ICCSN of the SMC-B to use; empty picks the first one found. |

### ZETA

| Variable | Default | Notes |
|---|---|---|
| `ZETA_SCOPE` | `vsdservice` | Validated against the guard's `scopes_supported`. |
| `ZETA_ASL_PROD` | `false` | `false` for DEV/RU/TU; only production targets use `true`. |
| `ZETA_CLIENT_DISABLE_SERVER_VALIDATION` | `true` | |
| `ZETA_REQUIRED_ROLE_OID` | empty | |
| `ZETA_STORAGE_AES_B64_KEY` | hardcoded sample key | See below. |

### ZETA storage key

`application.yaml` ships a hardcoded fallback key. Omitting the variable does
**not** give you in-memory storage — you get persistent storage encrypted with
a key published in a public sample repository. Either set your own:

```bash
openssl rand -base64 32
```

or set it to empty (`ZETA_STORAGE_AES_B64_KEY=`) to force `InMemoryStorage`, at
the cost of re-registering with ZETA on every restart.

### Settings not in the env file

Any key in `application.yaml` can be set here — Spring Boot relaxed binding
maps `service-discovery.url` to `SERVICE_DISCOVERY_URL`,
`logging.level.de.gematik` to `LOGGING_LEVEL_DE_GEMATIK`, and so on.

## Start

```bash
sudo systemctl start vsdm2-client
sudo journalctl -u vsdm2-client -f
ss -tlnp | grep 8083
```

There is no springdoc or actuator in this module, so there is no HTTP endpoint
to probe — confirm startup via the journal's "Started" line and the listening
socket. The API is `POST /vsdm`.

Then point popp-client at it and restart that service:

```sh
# /etc/default/popp-client
VSDM2_CLIENT_URL=http://localhost:8083
```

A successful card read then logs
`| VSDM 2.0 data for inserted eGK ... published` in popp-client, instead of
`| No vsdm2-client.url configured, skipping VSDM 2.0 fetch`.

## Package layout

| Path | Notes |
|---|---|
| `/usr/share/vsdm2-client/vsdm2-client.jar` | Spring Boot fat jar |
| `/usr/lib/systemd/system/vsdm2-client.service` | unit |
| `/etc/default/vsdm2-client` | config, dpkg **conffile**, mode 0640 |
| `/etc/vsdm2-client/` | keystores, `root:vsdm2-client` mode 0750 |
| `/var/lib/vsdm2-client/` | `StateDirectory`, working directory |

Port: 8083 (HTTP).

## Upgrade

```bash
scp vsdm2-client/target/vsdm2-client_2.7.0_all.deb user@HOST:/tmp/
sudo dpkg -i /tmp/vsdm2-client_2.7.0_all.deb
sha256sum /usr/share/vsdm2-client/vsdm2-client.jar
```

Use `dpkg -i` when the version string is unchanged — `apt install` reports
"already the newest version" and does nothing, silently leaving the old jar in
place. Always confirm the jar hash changed before concluding a fix did not work.

Your configuration is preserved: `/etc/default/vsdm2-client` is a conffile
(`dpkg -i --force-confold` never prompts), and keystores, systemd drop-ins and
`/var/lib/vsdm2-client` are not owned by the package.

The postinst runs `try-restart`, which will not start a stopped or failed
service — use `systemctl start` after a crash loop.

## Remove

```bash
sudo apt remove vsdm2-client     # keeps /etc and /var/lib
sudo apt purge vsdm2-client      # also removes them
```

## Troubleshooting

**`NoClassDefFoundError: .../ConnectorServices`.** The jar was built
incrementally. Rebuild with `clean` and verify the packaged jar before
deploying — see [Build](#build). Confirm the installed artifact is the one you
just built:

```bash
sha256sum /usr/share/vsdm2-client/vsdm2-client.jar
python3 -c "import zipfile,io;z=zipfile.ZipFile('/usr/share/vsdm2-client/vsdm2-client.jar');n=[x for x in z.namelist() if 'api-telematik' in x];i=zipfile.ZipFile(io.BytesIO(z.read(n[0])));print([x for x in i.namelist() if 'ConnectorServices' in x])"
```

**Startup hangs during ZETA authentication.** The SMC-B PIN may need
verification at the card terminal — the Konnektor's `VerifyPin` blocks until
it is entered. Same behaviour as popp-client; see `popp-client/INSTALL.md`.

**ZETA debug logging.** Use a drop-in so the conffile stays clean:

```bash
sudo systemctl edit vsdm2-client
```

```ini
[Service]
Environment=LOGGING_LEVEL_DE_GEMATIK_ZETA=DEBUG
Environment=LOGGING_LEVEL_IO_KTOR=DEBUG
```

Revert with `sudo systemctl revert vsdm2-client`.
