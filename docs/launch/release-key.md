Not legal advice. Last reviewed 2026-10-09. Check with a lawyer before a public launch.

# Lethenon: the release key - revocation certificate, a signing subkey for CI, the primary key offline

A runbook for the maintainer, to be carried out on the machine that holds the key. It covers five steps:

- a revocation certificate, stored offline;
- a subkey that only signs, valid for two years, for CI;
- the primary key moved offline afterwards;
- the CI secrets switched to the subkey;
- the fingerprint published in the README, on the website and on keyservers.

Every command below was rehearsed on a throwaway key of the same shape, with GnuPG 2.4.8 and lethenon's own build (section "How this was rehearsed"); the real key was only read.

## The key today

Read on 2026-10-09 with `gpg --list-secret-keys --keyid-format long D8C403518C49CA75` and
`gpg --verify` on the signature of lethenon 0.3.0 from Maven Central:

| | |
|---|---|
| primary key | `D8C403518C49CA75`, RSA 2048, created 2016-05-01, usage sign and certify (`[SC]`), no expiry date |
| fingerprint | `5B2F A6B1 1E29 8BC0 9287 F423 D8C4 0351 8C49 CA75` |
| subkeys | `1C664D394E31A6E2`, RSA 2048, encryption (`[E]`) |
| where the secret primary key is | on the maintainer's machine (`sec`, not `sec#`), and in the CI secret `GPG_PRIVATE_KEY` of six repositories: lethenon, mystic-crypt, crypt-data, crypt-api, resourcebundle-core, checksum-up (`gh secret list --repo astrapi69/<repo>`, names only) |
| revocation certificate | none: `~/.gnupg/openpgp-revocs.d/` holds no file for this fingerprint (GnuPG 2.1 and later write one when a key is created; this key predates that) |
| keyservers | on keyserver.ubuntu.com (`gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys` imports it); not on keys.openpgp.org (`https://keys.openpgp.org/vks/v1/by-fingerprint/5B2FA6B11E298BC09287F423D8C403518C49CA75` answers HTTP 404) |
| what it signs | lethenon 0.3.0 on Maven Central carries a good signature by it, made in the publish workflow from the CI secret |

## Read this first: Maven Central and subkeys

Central's own page on signatures warns: "Sub keys are a problem if you use them to sign and deploy
artifacts to the Central Repository, because Maven and Nexus Repository Manager can only verify
against a primary key." A report from June 2025 describes a bundle signed by a subkey that the
Central Portal accepted once the public key, subkey included, was on keys.openpgp.org. That is one
report, not Central's documentation. So the order below keeps everything reversible until a real
release has passed Central's validation:

- The public key with the new subkey reaches the keyservers before anything is signed with it
  (step 5).
- The first release signed by the subkey waits in the Central Portal for the maintainer's release,
  as every lethenon release does ([README](../../README.md), "Releasing", step 4). Its validation is
  read there before it is released. If the signature is refused, the deployment is dropped, not
  released, and the decision goes back to the maintainer: the primary key in CI as before, or local
  signing.
- The primary key leaves the machine only after that (step 9). Until then it stays, and the offline
  copies of step 4 exist from the start.

## Before you start

- **lethenon's build must know the subkey's id.** Gradle's in-memory signing otherwise takes the
  primary key, which an export of the subkey alone holds only as a stub, and fails. This is #154,
  fixed by #155 (`GPG_KEY_ID`).
- **The other five repositories need the same change** before their secrets switch:
  [mystic-crypt#190](https://github.com/astrapi69/mystic-crypt/issues/190),
  [crypt-data#91](https://github.com/astrapi69/crypt-data/issues/91),
  [crypt-api#27](https://github.com/astrapi69/crypt-api/issues/27),
  [resourcebundle-core#30](https://github.com/astrapi69/resourcebundle-core/issues/30),
  [checksum-up#6](https://github.com/astrapi69/checksum-up/issues/6). The primary key is offline only
  once all six secrets hold the subkey.
- **Two offline media**, for example two USB sticks, kept in different places, and a third place for
  the revocation certificate. Later work with the primary key (renewing the subkey, section 11) is
  best done on a machine without network.
- **GnuPG 2.2 or later** (`gpg --version`; rehearsed with 2.4.8), and `gh` logged in.
- On a German system gpg asks `(j/N)`: answer `j` where this text says `y`.

Every block below sets the variables it uses, so each one runs on its own, in a new terminal, in
bash and in zsh. A fingerprint followed by `!` stands in single quotes: zsh reads `!` inside double
quotes as history expansion and waits with `dquote>` (measured with zsh 5.9; bash 5.3.9 runs either
form; #168). First, the key as it is:

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
gpg --list-secret-keys --keyid-format long --with-subkey-fingerprints "$FPR"
```

Expected: `sec rsa2048/D8C403518C49CA75 2016-05-01 [SC]` and `ssb rsa2048/1C664D394E31A6E2 2016-05-01 [E]`.

## 1. A safety copy of the whole key

Before anything changes. The export is protected by the key's passphrase only.

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
mkdir -m 700 ~/release-key-offline
gpg --armor --export-secret-keys "$FPR" > ~/release-key-offline/D8C403518C49CA75-secret-before.asc
gpg --armor --export "$FPR" > ~/release-key-offline/D8C403518C49CA75-public-before.asc
gpg --export-ownertrust > ~/release-key-offline/ownertrust.txt
```

Copy the folder onto both offline media.

## 2. The revocation certificate

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
gpg --armor --output ~/release-key-offline/D8C403518C49CA75-revoke.asc --generate-revocation "$FPR"
```

gpg asks, in this order:

1. "Create a revocation certificate for this key? (y/N)": `y`
2. the reason: `0` (no reason specified), so that the certificate fits whatever happens
3. a description: an empty line
4. "Is this okay? (y/N)": `y`
5. the key's passphrase

Store it apart from the key copies: on paper (it is a short block of text) and on a medium in the
third place. Whoever holds it can revoke the key, but cannot sign with it. **Do not import it**:
importing it revokes the key at once (rehearsed, section "How this was rehearsed").

## 3. A subkey that only signs, for two years

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
gpg --quick-add-key "$FPR" rsa4096 sign 2y
gpg --list-keys --keyid-format long --with-subkey-fingerprints "$FPR"
```

A new line appears: `sub rsa4096/<16 hex digits> <today> [S] [expires: <today + 2 years>]`, with its
fingerprint below it. The subkey made on 2026-10-10 is `rsa4096/9FCF7C9710E2BD8D`, valid until
2028-10-09, and the blocks below carry its fingerprint; a later subkey (section 12) puts its own in
their place. `SUBID` is the fingerprint's last 8 hex digits:

```sh
export SUBFPR=6D67F8442A6FCC96BD7C1B5E9FCF7C9710E2BD8D
export SUBID=10E2BD8D
```

From now on gpg signs with this subkey by default, as the newest key that can sign. RSA like the
primary key: it is what the rehearsal signed with through Gradle; Ed25519 was not rehearsed.

## 4. The offline copies, with the subkey

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
gpg --armor --export-secret-keys "$FPR" > ~/release-key-offline/D8C403518C49CA75-secret.asc
gpg --armor --export "$FPR" > ~/release-key-offline/D8C403518C49CA75-public.asc
```

Copy both onto the two offline media, next to the safety copy of step 1. Then check one medium in a
keyring of its own: the primary key must be complete (`sec`, not `sec#`), with the new `[S]` subkey.

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
export GNUPGHOME="$(mktemp -d)"
gpg --import /path/to/medium/D8C403518C49CA75-secret.asc
gpg --list-secret-keys --keyid-format long "$FPR"
gpgconf --kill gpg-agent; rm -rf "$GNUPGHOME"; unset GNUPGHOME
```

## 5. Publish the public key with the subkey

Before anything is signed with the subkey: Central and every user check a signature against the
public key on a keyserver, and the subkey has to be in it. Central reads keyserver.ubuntu.com,
keys.openpgp.org and pgp.mit.edu (Central, "GPG").

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys "$FPR"
```

For keys.openpgp.org, upload `~/release-key-offline/D8C403518C49CA75-public.asc` at
<https://keys.openpgp.org/upload> and confirm the e-mail it sends. Without that confirmation it
distributes the key without name and e-mail address: "We require explicit consent to distribute
identity information" (keys.openpgp.org, FAQ).

Check both, in an empty keyring:

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
export GNUPGHOME="$(mktemp -d)"
gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys "$FPR"
gpg --list-keys --keyid-format long "$FPR"
curl -sSf "https://keys.openpgp.org/vks/v1/by-fingerprint/$FPR" | gpg --show-keys --keyid-format long
gpgconf --kill gpg-agent; rm -rf "$GNUPGHOME"; unset GNUPGHOME
```

Both listings show the `[S]` subkey with its expiry date.

## 6. The CI copy: the subkey alone, with a passphrase of its own

The CI secret gets the signing subkey and nothing else, protected by a passphrase that is not the
primary key's.

```sh
export CIHOME="$HOME/release-key-ci"
mkdir -m 700 "$CIHOME" "$CIHOME/gnupg"
gpg --armor --export-secret-subkeys '6D67F8442A6FCC96BD7C1B5E9FCF7C9710E2BD8D!' > "$CIHOME/subkey.asc"
GNUPGHOME="$CIHOME/gnupg" gpg --import "$CIHOME/subkey.asc"
GNUPGHOME="$CIHOME/gnupg" gpg --list-secret-keys --keyid-format long
```

Expected: `sec#` (the primary key is not there) and a single `ssb rsa4096/... [S]`. The `!` after the
fingerprint is what limits the export to that one subkey; it stands with the fingerprint in single
quotes, so that zsh does not take it for history expansion.

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
export CIHOME="$HOME/release-key-ci"
GNUPGHOME="$CIHOME/gnupg" gpg --passwd "$FPR"
```

gpg asks for the current passphrase, then twice for the new one, which is the CI passphrase. It also
reports an error for the primary key, "No secret key", which is expected: only the subkey is there,
and its passphrase is changed. Then export it for CI:

```sh
export CIHOME="$HOME/release-key-ci"
GNUPGHOME="$CIHOME/gnupg" gpg --armor --export-secret-subkeys '6D67F8442A6FCC96BD7C1B5E9FCF7C9710E2BD8D!' > "$CIHOME/ci-subkey.asc"
```

## 7. Switch the CI secrets

All six repositories at once, each once its issue under "Before you start" is merged. The CI
passphrase is read first, without echo, in a block of its own: a question in the middle of a block
takes the next pasted line as its answer (#170). Paste this line alone, type the passphrase, press
Enter:

```sh
printf 'CI passphrase: '; read -rs CIPW; echo
```

Then, in the same terminal, the three secrets. The block first checks that the passphrase signs
with the CI copy, after stopping that keyring's agent so that a cached passphrase cannot hide a
typo, and sets nothing otherwise:

```sh
export CIHOME="$HOME/release-key-ci"
export SUBID=10E2BD8D
GNUPGHOME="$CIHOME/gnupg" gpgconf --kill gpg-agent
if [ -n "${CIPW:-}" ] && GNUPGHOME="$CIHOME/gnupg" gpg --batch --yes --pinentry-mode loopback --passphrase-fd 3 --local-user '9FCF7C9710E2BD8D!' --output /dev/null --sign "$CIHOME/ci-subkey.asc" 3<<<"$CIPW"; then
  for repo in lethenon mystic-crypt crypt-data crypt-api resourcebundle-core checksum-up; do
    echo "== $repo"
    gh secret set GPG_PRIVATE_KEY --repo "astrapi69/$repo" < "$CIHOME/ci-subkey.asc"
    printf '%s' "$CIPW" | gh secret set GPG_PASSPHRASE --repo "astrapi69/$repo"
    gh secret set GPG_KEY_ID --repo "astrapi69/$repo" --body "$SUBID"
  done
else
  echo "NOT SET: the passphrase is empty or does not sign with the CI copy - read it again"
fi
unset CIPW
```

The passphrase reaches `gh` on its standard input, through `printf`, a shell builtin, so it is on no
command line, in no process list and in no shell history. `GPG_KEY_ID` is the subkey's last 8 hex
digits. It is not secret, and it is a secret only so that all three travel together; Gradle 9.7.1
refuses the 16-digit form. This is how the secrets were set on 2026-10-10.

## 8. Rehearse before a release depends on it

The build, signing as CI will, from the CI copy. The CI passphrase is read without echo, in a block
of its own:

```sh
printf 'CI passphrase: '; read -rs GPG_PASSPHRASE; echo
```

Then, in the same terminal:

```sh
export SUBID=10E2BD8D
export CIHOME="$HOME/release-key-ci"
export GPG_PASSPHRASE
cd "$HOME/dev/git/hub/astrapi69/lethenon" && git switch develop && git pull --ff-only
GPG_PRIVATE_KEY="$(cat "$CIHOME/ci-subkey.asc")" GPG_KEY_ID="$SUBID" ./gradlew signMavenJavaPublication
unset GPG_PASSPHRASE
V=$(sed -n 's/^projectVersion=//p' gradle.properties)
gpg --verify "build/libs/lethenon-$V.jar.asc" "build/libs/lethenon-$V.jar"
```

Expected: "Good signature", made "using RSA key" with the subkey's fingerprint.

Then the workflow itself, with a snapshot (`publish.yml` asks for such a run after any change to it):

```sh
gh workflow run publish.yml --repo astrapi69/lethenon --ref develop -f target=snapshot
gh run watch --repo astrapi69/lethenon \
  "$(gh run list --repo astrapi69/lethenon --workflow publish.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
```

The first release after the switch is then read in the Central Portal before it is released
(see "Read this first"). After its release, the README's "Releasing" step 5 verifies the signatures
from Central; they name the subkey.

## 9. The primary key offline

Only after step 8 has passed, at the latest after the first release validated with the subkey. The
machine keeps the subkeys, for signing and for decrypting; the primary key stays on the offline media
only.

Before it: two offline media hold a complete copy of the key, the primary key itself and not a
stub. Step 4's check in a keyring of its own shows that as `sec`, where a stub shows `sec#`. Run on
the export file directly, `gpg --show-keys` prints `sec` for a stub too; `gpg --list-packets` tells
them apart, because a stub is a packet marked `gnu-dummy` (measured on 2026-10-10 with a throwaway
key).

Each of the next three blocks is pasted on its own, because the second one asks.

First the subkeys, into a folder of its own; the CI folder of step 6 may be gone already:

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
export STEP9="$HOME/release-key-step9"
mkdir -m 700 "$STEP9"
gpg --armor --export-secret-subkeys "$FPR" > "$STEP9/daily-subkeys.asc"
```

Without a `!` after the fingerprint, `--export-secret-subkeys` exports every subkey. Then the secret
key, with all its subkeys. gpg asks for confirmation for the key and for each subkey:

```sh
gpg --delete-secret-keys 5B2FA6B11E298BC09287F423D8C403518C49CA75
```

Then the subkeys back:

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
export STEP9="$HOME/release-key-step9"
gpg --import "$STEP9/daily-subkeys.asc"
gpg --list-secret-keys --keyid-format long "$FPR"
```

Expected afterwards: `sec#`, `ssb ... [E]` and `ssb ... [S]`. Signing still works and uses the subkey; whatever needs the primary
key (a new subkey, a renewal, a revocation) now fails on this machine with "No secret key", as it
should (rehearsed).

Then remove the working copies from the machine:

```sh
export CIHOME="$HOME/release-key-ci"
export STEP9="$HOME/release-key-step9"
gpgconf --kill gpg-agent
rm -rf "$CIHOME" "$STEP9"
rm -rf ~/release-key-offline
```

Only after the offline media were checked in step 4. On an SSD or a journaling file system, deleting
does not reliably erase. The files are protected by their passphrases, which is what makes this
acceptable; it is a reason to keep those passphrases strong.

Two checks on the daily machine, since local signing goes through `gpg`:

- mystic-crypt-ui signs its releases locally;
- the libraries can sign locally with `signing.gnupg.keyName`.

With the key's id as the name, gpg picks the signing subkey. This was not rehearsed with those
builds; sign one snapshot locally and run `gpg --verify` on it before the next release.

## 10. Publish the fingerprint

- **README**, a section of its own, after the first release signed by the subkey:

  ```markdown
  ## Release signatures

  Releases are signed with the OpenPGP key
  `5B2F A6B1 1E29 8BC0 9287 F423 D8C4 0351 8C49 CA75`, since <first release> by its signing subkey
  `<SUBFPR, in groups of four>`, valid until <expiry date>. The key is on keyserver.ubuntu.com and
  keys.openpgp.org. Verify a download with `gpg --verify <file>.asc <file>`.
  ```

- **Website**: the same paragraph on the project's site (`infrastructure.md`, section 8), once it
  exists.
- **Keyservers**: step 5, and again after every change to the key (renewal, new subkey,
  revocation).

## 11. Every two years: renew the subkey

Two months before it expires, on the offline machine with a medium from step 4:

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
export SUBFPR=6D67F8442A6FCC96BD7C1B5E9FCF7C9710E2BD8D
gpg --import /path/to/medium/D8C403518C49CA75-secret.asc
gpg --quick-set-expire "$FPR" 2y "$SUBFPR"
gpg --armor --export "$FPR" > D8C403518C49CA75-public.asc
gpg --armor --export-secret-keys "$FPR" > /path/to/medium/D8C403518C49CA75-secret.asc
```

On the online machine: `gpg --import D8C403518C49CA75-public.asc`, then publish it again (step 5).
The renewal changes the expiry date only, not the subkey, so the CI secrets stay as they are. Update
the date in the README.

## 12. When something goes wrong

**The CI secret leaked, or the subkey is compromised.** On the offline machine, revoke the subkey:

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
gpg --edit-key "$FPR"
```

At the prompt:

1. `key 6D67F8442A6FCC96BD7C1B5E9FCF7C9710E2BD8D` selects the subkey;
2. `revkey` revokes it: confirm, reason `1` (key has been compromised), an empty description,
   confirm again;
3. `save`.

Then export the public key and publish it (step 5), and start again with a new subkey from step 3.
gpg still reports an earlier signature by the revoked subkey as good, with the warning that the
subkey was revoked by its owner and the reason given (rehearsed).

**The primary key is compromised or lost.** Import the revocation certificate, then publish:

```sh
export FPR=5B2FA6B11E298BC09287F423D8C403518C49CA75
gpg --import D8C403518C49CA75-revoke.asc
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys "$FPR"
```

Upload the revoked key at keys.openpgp.org as in step 5. A new key follows, with its fingerprint
published as in step 10. gpg then reports a signature by the old key as good, with the warning that
the key was revoked by its owner and that the signature could be forged (rehearsed).

## How this was rehearsed

On 2026-10-09, with GnuPG 2.4.8 in a `GNUPGHOME` of its own under `~/.cache/release-key-test/`; the
maintainer's `~/.gnupg` was only read. The throwaway key had the shape of the real one: RSA 2048
primary for sign and certify, no expiry, an encryption subkey.

- **Steps 2 and 3**: a revocation certificate (made through a pseudo terminal, because gpg refuses
  `--generate-revocation` in batch mode: "Dies kann im Batchmodus nicht durchgeführt werden"), and a
  subkey `rsa4096 [S]`, which expired two years later.
- **Step 6**: the subkey alone imported elsewhere showed `sec#` and one `ssb [S]`. `--passwd`
  reported the expected error for the primary key; afterwards the old passphrase was refused and the
  new one signed.
- **The build** (lethenon's own `signMavenJavaPublication`, at #155's commit):

  | Secrets | Result |
  |---|---|
  | subkey alone, its 8-digit id | good signature, made by the subkey |
  | full key, no id | good signature, made by the primary key |
  | subkey alone, no id | exit 1 |

  A separate Gradle 9.7.1 probe refused the 16-digit id.
- **Step 9**: after the secret keys were deleted and the subkeys imported again, the machine showed
  `sec#`, still signed (by the subkey), and failed to extend the subkey: "No secret key".
- **Steps 11 and 12**:
  - From the full copy, the renewal moved the expiry date to a new one.
  - The subkey was revoked with `revkey`.
  - The imported revocation certificate revoked the whole key.
  - An earlier signature by the subkey then still verified as good, with a warning in both cases.

On 2026-10-10, with the real key, and in a sandbox:

- **Step 7 as written:** run in an interactive zsh and bash against a throwaway key, with `gh`
  replaced by a function that records what it receives.
  - An empty or a wrong passphrase set nothing and printed "NOT SET".
  - The right one set 18 secrets; the passphrase arrived only on standard input, six times, and never
    on the screen (#170).
- **The workflow part of step 8:** in mystic-crypt, run 38046383079. Its snapshot
  `13.6-20261010.105221-4` verified, in an empty keyring holding only the key from keys.openpgp.org,
  as signed by `6D67F8442A6FCC96BD7C1B5E9FCF7C9710E2BD8D`. The first release after it, mystic-crypt
  13.6, passed the Central Portal's validation and verified the same way on repo1.

Not rehearsed:

- the keyserver uploads, which would have published the test key;
- Central's validation of a subkey signature, which only a real deployment shows;
- `gh secret set`;
- local signing in mystic-crypt-ui and the libraries.

## Sources

Retrieved 2026-10-09.

- Maven Central, "GPG" (keyservers supported by Central; "Delete a Sub Key"):
  <https://central.sonatype.org/publish/requirements/gpg/>
- keys.openpgp.org, FAQ (identity information only with consent): <https://keys.openpgp.org/about/faq>;
  upload: <https://keys.openpgp.org/upload>
- A report of a subkey-signed bundle accepted by the Central Portal, 21 June 2025 (one person's
  report, not documentation):
  <https://matthovey.wordpress.com/2025/06/21/maven-central-gpg-the-signature-saga-i-didnt-expect/>
- GnuPG 2.4.8, the options used: `--generate-revocation`, `--quick-add-key`,
  `--export-secret-subkeys` (with `!`), `--passwd`, `--quick-set-expire`, `--edit-key` (`revkey`),
  `--delete-secret-keys`, `--send-keys`, `--recv-keys`, `--show-keys`; executed as described above.
- Gradle signing, `useInMemoryPgpKeys` with and without a key id: lethenon #154, #155.
