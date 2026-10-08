Not legal advice. Last reviewed 2026-10-08. Check with a lawyer before a public launch.

# Lethenon: regulatory overview

What the rules that a public start of lethenon's main chain runs into say, each statement with the
provision it comes from. It describes; it does not conclude whether a particular plan is lawful.
Where the text leaves a question open, this document says that it is open. The short form, as a
table of what needs an authorisation and what does not, is
[launch-checklist.md](launch-checklist.md). All sources were retrieved on 2026-10-08 and are listed
at the end; the law cited is the text published in the Official Journal, without later amendments
unless one is named.

## 1. MiCA: the EU's regulation on markets in crypto-assets

Regulation (EU) 2023/1114 ("MiCA"), applicable since 30 December 2024 (Article 149(2)).

### 1.1 What it covers

MiCA "applies to natural and legal persons and certain other undertakings that are engaged in the
issuance, offer to the public and admission to trading of crypto-assets or that provide services
related to crypto-assets in the Union" (Article 2(1)). A crypto-asset is "a digital representation
of a value or of a right that is able to be transferred and stored electronically using distributed
ledger technology or similar technology" (Article 3(1)(5)).

An "offer to the public" is "a communication to persons in any form, and by any means, presenting
sufficient information on the terms of the offer and the crypto-assets to be offered so as to
enable prospective holders to decide whether to purchase those crypto-assets" (Article 3(1)(12)).

Where crypto-assets "have no identifiable issuer, they should not fall within the scope of Title
II, III or IV"; crypto-asset service providers providing services in respect of them "should,
however, be covered" (recital 22). Services "provided in a fully decentralised manner without any
intermediary" are outside the Regulation (recital 22).

### 1.2 The exemption for mined crypto-assets: Article 4(3)(b) and recital 26

Title II - the duties of an offeror: to be a legal person, to draw up, notify and publish a
crypto-asset white paper (Article 4(1)) - "shall not apply to offers to the public of crypto-assets
other than asset-referenced tokens or e-money tokens where any of the following apply: ... (b) the
crypto-asset is automatically created as a reward for the maintenance of the distributed ledger or
the validation of transactions" (Article 4(3)).

Recital 26 gives the reason: "In order to ensure a proportionate approach, no requirements of this
Regulation should apply to offers to the public of crypto-assets other than asset-referenced tokens
or e-money tokens that are offered for free or that are automatically created as a reward for the
maintenance of a distributed ledger or the validation of transactions in the context of a consensus
mechanism."

LETH comes into circulation only as block rewards: the whole supply starts in the mining pool, and
every block, the genesis block included, takes one reward out of it, with nothing allocated to
anybody before the first block is mined ([specification.md](specification.md), section 2;
`GenesisAllocationTest`; #111).

A neighbouring exemption, for crypto-assets "offered for free" (Article 4(3)(a)), has a limit of its
own: a crypto-asset "shall not be considered to be offered for free where purchasers are required to
provide, or to undertake to provide, personal data to the offeror in exchange for that crypto-asset"
or the offeror receives "any fees, commissions, or monetary or non-monetary benefits" in exchange
(Article 4(3), second subparagraph).

### 1.3 How the exemption is lost: Article 4(4)

"The exemptions listed in paragraphs 2 and 3 shall not apply where the offeror, or another person
acting on the offeror's behalf, makes known in any communication its intention to seek admission to
trading of a crypto-asset other than an asset-referenced token or e-money token" (Article 4(4)).
Recital 26 adds the second case: the exemptions "should cease to apply when the offeror, or another
person acting on the offeror's behalf, communicates the offeror's intention of seeking admission to
trading or the exempted crypto-assets are admitted to trading."

Admission to trading itself requires a legal person and a white paper drawn up, notified and
published under MiCA (Article 5(1)). It is not only in the hands of whoever made the crypto-asset:
"When a crypto-asset is admitted to trading on the initiative of the operator of a trading platform
and a crypto-asset white paper has not been published ... the operator of that trading platform
for crypto-assets shall comply with the requirements set out in paragraph 1" (Article 5(2)).

### 1.4 The voluntary white paper

"Where an offer to the public of a crypto-asset other than an asset-referenced token or e-money
token is exempt from the obligation to publish a crypto-asset white paper under paragraph 2 or 3,
but a white paper is nevertheless drawn up voluntarily, this Title shall apply" (Article 4(8)).
This is why lethenon's technical description is called [specification.md](specification.md) and
says why in its first paragraph.

### 1.5 Selling, exchange and custody: the crypto-asset services

MiCA lists ten crypto-asset services (Article 3(1)(16)), among them "(a) providing custody and
administration of crypto-assets on behalf of clients", "(b) operation of a trading platform for
crypto-assets", "(c) exchange of crypto-assets for funds", "(d) exchange of crypto-assets for other
crypto-assets" and "(j) providing transfer services for crypto-assets on behalf of clients".

- **Who needs an authorisation.** "A person shall not provide crypto-asset services, within the
  Union, unless that person is" an authorised legal person or other undertaking, or one of the
  financial institutions allowed to provide them (Article 59(1)). A crypto-asset service provider
  is "a legal person or other undertaking whose occupation or business is the provision of one or
  more crypto-asset services to clients on a professional basis" (Article 3(1)(15)). An authorised
  provider "shall have a registered office in a Member State where they carry out at least part of
  their crypto-asset services", its "place of effective management in the Union and at least one of
  the directors shall be resident in the Union" (Article 59(2)).
- **Selling.** An offer to the public of mined LETH is outside Title II (section 1.2). Exchanging
  crypto-assets for funds or for other crypto-assets as a service is a crypto-asset service (Article
  3(1)(16)(c) and (d)); a provider of it "shall establish a non-discriminatory commercial policy"
  (Article 77(1)).
- **Exchange (trading platform).** Its operator "shall lay down, maintain and implement clear and
  transparent operating rules" (Article 76(1)); admission to trading is section 1.3.
- **Custody.** Custody means "the safekeeping or controlling, on behalf of clients, of crypto-assets
  or of the means of access to such crypto-assets, where applicable in the form of private
  cryptographic keys" (Article 3(1)(17)); a custodian "shall conclude an agreement with their
  clients to specify their duties and their responsibilities" (Article 75(1)). lethenon's wallet
  makes and keeps the keys on the user's own machine (`SeedPhrase`, `WalletFile`).
- **Custody and transfer of exempt crypto-assets.** "Authorisation as a crypto-asset service
  provider pursuant to Article 59 is not required for providing custody and administration of
  crypto-assets on behalf of clients or for providing transfer services for crypto-assets in
  relation to crypto-assets whose offers to the public are exempt pursuant to paragraph 3 of this
  Article, unless: (a) there exists another offer to the public of the same crypto-asset and that
  offer does not benefit from the exemption; or (b) the crypto-asset offered is admitted to a
  trading platform" (Article 4(5)). Exchange and the other services are not named there.
- **Nodes and miners.** A transfer service "should not include the validators, nodes or miners that
  might be part of confirming a transaction and updating the state of the underlying distributed
  ledger" (recital 93).

## 2. AMLR: the EU's anti-money-laundering regulation, Article 79

Regulation (EU) 2024/1624 ("AMLR") applies "from 10 July 2027", for two groups of obliged entities
from 10 July 2029 (Article 90). Crypto-asset service providers are financial institutions under it
(Article 2(1)(6)(i)) and therefore obliged entities (Article 3(2)).

### 2.1 The prohibition

"Credit institutions, financial institutions and crypto-asset service providers shall be prohibited
from keeping anonymous bank and payment accounts, anonymous passbooks, anonymous safe-deposit boxes
or anonymous crypto-asset accounts as well as any account otherwise allowing for the anonymisation
of the customer account holder or the anonymisation or increased obfuscation of transactions,
including through anonymity-enhancing coins" (Article 79(1)).

"'anonymity-enhancing coins' means crypto-assets that have built-in features designed to make
crypto-asset transfer information anonymous, either systematically or optionally" (Article
2(1)(25)).

Recital 160 bounds it: "That prohibition does not apply to providers of hardware and software or
providers of self-hosted wallets insofar as they do not possess access to or control over those
crypto-asset wallets."

The prohibition is addressed to obliged entities: banks, financial institutions, crypto-asset
service providers. It does not forbid a crypto-asset; it forbids those entities to keep accounts
that allow it (Article 79(1)).

### 2.2 The open question: is the hidden recipient such a feature?

Open. Nothing below answers it; it collects what a lawyer would need.

What lethenon's hidden recipient does, from the repository:

- A sender can pay a published address (`send --to-address`). The payment then goes to a one-time
  destination derived from that address, "so two payments to the same address land on unrelated
  destinations" ([README](../../README.md), "Privacy, and its honest label"; `OneTimeAddresses`,
  `OneTimeAddressesTest`). Only the recipient, with its view key, finds the payment (`WalletScan`,
  `WalletScanTest`).
- It is chosen per transfer: `send --to` names an account in the clear, and "the chain shows who was
  paid" ([README](../../README.md), "Command line"; `AddressScheme`, `direct` and `stealth-v2`).
- What stays public: "The sender is a public key in the clear and the amount a plain number in every
  transaction" ([README](../../README.md), "Privacy, and its honest label"). The one-time destination
  itself is on the chain; what it hides is which published address it belongs to.
- Spending ends it: once `sweep` has moved the payments onto the account, "the chain then shows
  those destinations and the account together" ([README](../../README.md), "Command line";
  `SweepsTest`).

What the text gives to weigh, as it reads:

- The definition reaches features that work "optionally" (Article 2(1)(25)); the hidden recipient
  is optional and built in.
- The definition speaks of making "crypto-asset transfer information anonymous". The AMLR does not
  define that term: the phrase occurs once in the Regulation, in the definition itself (counted in
  the published XHTML with `grep -c "crypto-asset transfer information" amlr.html`, result 1). Which
  information it means - sender, recipient, amount, the link between a payment and an address - is
  therefore not fixed by the AMLR.
- lethenon's transfers carry a public sender and a public amount; what is hidden is only the link
  between a destination and the recipient's published address.
- Recital 160 keeps software providers and self-hosted wallets without access to the wallets out of
  the prohibition. lethenon is software with a self-hosted wallet.

If the hidden recipient is such a feature, the consequence falls on obliged entities: from 10 July
2027 no bank, financial institution or crypto-asset service provider in the EU may keep an account
that allows transactions with it (Article 79(1), Article 90). lethenon plans no such entity: "Nobody
can buy it, there is no pool, no listing, no promotion, no sale" ([README](../../README.md), "What it
is not").

## 3. Dubai: VARA and DFSA

Dubai has two regulators for virtual assets: the Virtual Assets Regulatory Authority (VARA) for the
Emirate outside the Dubai International Financial Centre, and the Dubai Financial Services Authority
(DFSA) inside it. Both prohibit coins whose features hide transactions, each with a definition of
its own.

### 3.1 VARA: "Anonymity-Enhanced Cryptocurrencies"

- **The prohibition.** "The issuance of Anonymity-Enhanced Cryptocurrencies and all VA
  Activity(ies) related to them are prohibited in the Emirate" (Virtual Assets and Related
  Activities Regulations 2023, Regulation II.C.1, numbered in VARA's own citation style; the same
  text in the consolidated version of 19 May 2025). The Virtual Asset Issuance Rulebook of 19 May
  2025 repeats it.
- **The definition.** An Anonymity-Enhanced Cryptocurrency "means a type of Virtual Asset which
  prevents the tracing of transactions or record of ownership through distributed public ledgers and
  for which the VASP has no mitigating technologies or mechanisms to allow traceability or
  identification of ownership" (Regulations 2023, Schedule 4). "Emirate" means "all zones across the
  Emirate of Dubai, including Special Development Zones and Free Zones but excluding the Dubai
  International Financial Centre" (same schedule).
- **Marketing.** "The Marketing of any Anonymity-Enhanced Cryptocurrencies in or targeting the UAE,
  and any VA Activity(ies) involving Anonymity-Enhanced Cryptocurrencies is strictly prohibited in the
  Emirate" (Marketing Regulations 2024, Marketing Regulation I.B.4). These regulations "are
  applicable to all Entities, including domestic and/or foreign Entities" (their introduction); an
  entity outside the Emirate is outside them only if it is not located there, conducts no VA activity
  there and does no marketing "in or targeting the UAE" (I.B.5).
- **A separate, softer term.** "Anonymity-Enhanced Transactions" - transactions in virtual assets
  that are not Anonymity-Enhanced Cryptocurrencies "but which prevent the tracing of transactions or
  record of ownership" - are not prohibited outright: a provider enabling them "must implement
  proportionately enhanced controls", and "In the case where the AML/CFT risks cannot be adequately
  mitigated, such products or services should not be offered" (Compliance and Risk Management
  Rulebook of 19 May 2025, Rule III.D.5 and its definitions).

### 3.2 DFSA: "Privacy Tokens" and "Privacy Devices"

- **The prohibition.** "A Person must not in or from the DIFC: (a) carry on a Financial Service
  relating to a Privacy Token or that involves the use of a Privacy Device; (b) make or approve a
  Financial Promotion relating to a Privacy Token or Privacy Device; (c) Offer to the Public a Privacy
  Token" (DFSA Rulebook, General Module, GEN 3A.2.2, in the version in force since 12 January 2026,
  which adds funds and derivatives relating to a Privacy Token in (d) and (e)).
- **The definitions.** A Privacy Device is "any technology, Digital Wallet or other mechanism or
  device (excluding a VPN), which has any feature or features used, or intended to be used, to hide,
  anonymise, obscure or prevent the tracing of" any of six kinds of information: "(i) a Crypto Token
  transaction; (ii) the identity of the holder of a Crypto Token; (iii) the cryptographic key
  associated with a person; (iv) the identity of parties to a Crypto Token transaction; (v) the value
  of a Crypto Token transaction; or (vi) the beneficial owner of a Crypto Token". A Privacy Token is a
  crypto token whose token or ledger "has any feature or features that are used, or intended to be
  used, to hide, anonymise, obscure or prevent the tracing of" the same information (GEN 3A.1.1(b)
  and (c)).
- **Optional features count.** "For example, some Crypto Tokens have features that can be turned on
  at the option of the user to hide or prevent the tracing of information. A Crypto Token that has such
  optional features, will be a Privacy Coin as defined and is prohibited from being used in the DIFC"
  (GEN 3A.2.2, Guidance, paragraph 2; the guidance says "Privacy Coin" where the rule's defined term is
  "Privacy Token").

### 3.3 What the definitions name, next to what lethenon does

Whether lethenon falls under either definition is the open question of section 2.2, put again for
each regulator; this document does not answer it. What can be set side by side:

- Unlike the AMLR, the DFSA's definition lists the information it covers, and "the identity of
  parties to a Crypto Token transaction" is on the list; its guidance counts features that are
  optional. lethenon's hidden recipient is an optional feature that keeps a payment from showing
  which published address it was made to (section 2.2).
- VARA's definition speaks of preventing "the tracing of transactions or record of ownership" and
  adds a condition about the provider's "mitigating technologies or mechanisms". In lethenon the
  recipient's view key is what recognises its payments (`WalletScan`, `WalletScanTest`).
- The rules prohibit activities "in the Emirate", marketing "in or targeting the UAE" and services
  "in or from the DIFC". lethenon plans no sale, listing or promotion anywhere
  ([README](../../README.md), "What it is not"), and its messaging guide does not call it a privacy
  coin, anonymous or untraceable ([messaging-guide.md](messaging-guide.md)).

## 4. Residence decides, not the seat of a company

The provisions above attach their duties to what happens in the Union and to the people there:

- MiCA applies to persons engaged in the issuance, offer or admission to trading of crypto-assets
  "or that provide services related to crypto-assets in the Union" (Article 2(1)), and the
  authorisation is required for services "within the Union" (Article 59(1)).
- A firm outside the Union is spared the authorisation only "Where a client established or situated
  in the Union initiates at its own exclusive initiative the provision of a crypto-asset service or
  activity". Where the firm "solicits clients or prospective clients in the Union, regardless of the
  means of communication used for the solicitation, promotion or advertising in the Union, it shall
  not be deemed to be a service provided on the client's own exclusive initiative", and that applies
  "notwithstanding any contractual clause or disclaimer purporting to state otherwise" (Article
  61(1)). The firm may not market "new types of crypto-assets or crypto-asset services" to such a
  client either (Article 61(2)).
- An authorised provider needs its place of effective management in the Union and a director
  resident there (Article 59(2)); "The place of effective management means the place where the key
  management and commercial decisions that are necessary for the conduct of the business are taken"
  (recital 74).
- ESMA's guidelines on reverse solicitation under MiCA (ESMA35-1872330276-2030, 26 February 2025)
  read the exception narrowly. "Solicitation includes the promotion, advertisement or offer of
  crypto-asset services or activities to clients or prospective clients in the Union by any means"
  (paragraph 12), brand advertising "may also constitute solicitation" (paragraph 13), "The client's
  own exclusive initiative should be construed narrowly" (paragraph 23), and "Contractual arrangements
  or disclaimers cannot supersede contrary facts" (paragraph 24). The solicitation "may be carried out
  by the third-country firm itself or any person acting on its behalf or having close links with the
  third-country firm" (paragraph 19). Close links are those of MiFID II (MiCA Article 3(1)(31)): two or
  more natural or legal persons linked by "participation in the form of ownership, direct or by way of
  control, of 20 % or more of the voting rights or capital of an undertaking", or by control
  (Directive 2014/65/EU, Article 4(1)(35)). ESMA's final report on the guidelines adds that the
  exception "may not be relied upon by EU-based firms to escape the authorisation or notification
  requirements under MiCA" (ESMA35-1872330276-1899, 17 December 2024, paragraph 10).
- What the guidelines do not contain: a residence test. They and MiCA speak of clients "established
  or situated" in the Union; the guidelines, the final report and the consultation paper before them
  (ESMA35-1872330276-1619) do not use the words "resident", "residence", "domicile" or "nationality"
  (searched in the text of the three PDFs with `grep -i -E 'resident|residen|domicil|nationalit'`, no
  match). No EU or ESMA text found addresses the case of a person living in the Union who runs a
  company outside it for clients in the Union; that is a negative over the documents listed under
  Sources, not a statement that no such text exists.
- The GDPR applies to processing "in the context of the activities of an establishment of a
  controller or a processor in the Union, regardless of whether the processing takes place in the
  Union or not", and to a controller outside the Union where the processing relates to "the
  offering of goods or services ... to such data subjects in the Union" (Regulation (EU) 2016/679,
  Article 3(1) and (2)(a)). It matters for the seed nodes, which see IP addresses
  ([infrastructure.md](infrastructure.md)).

None of these provisions asks first where a company is registered. A company outside the Union does
not take out of them what is done in the Union, for people in the Union, or managed from there.

## Sources

Retrieved 2026-10-08. The EU texts were read in the version published in the Official Journal,
fetched from the Publications Office (CELLAR) by their CELEX numbers.

- Regulation (EU) 2023/1114 on markets in crypto-assets (MiCA), CELEX 32023R1114: Articles 2(1),
  3(1)(5), (12), (15), (16) and (17), 4(1), (3), (4), (5) and (8), 5(1) and (2), 59(1) and (2),
  61(1) and (2), 75(1), 76(1), 77(1), 149(2); recitals 22, 26, 74 and 93.
  <https://eur-lex.europa.eu/eli/reg/2023/1114/oj>
- Regulation (EU) 2024/1624 on the prevention of the use of the financial system for the purposes of
  money laundering or terrorist financing (AMLR), CELEX 32024R1624: Articles 2(1)(6)(i) and (25),
  3(2), 79(1), 90; recital 160. <https://eur-lex.europa.eu/eli/reg/2024/1624/oj>
- Regulation (EU) 2016/679 (General Data Protection Regulation), CELEX 32016R0679: Article 3(1) and
  (2). <https://eur-lex.europa.eu/eli/reg/2016/679/oj>
- ESMA, Guidelines on situations in which a third-country firm is deemed to solicit clients
  established or situated in the EU and the supervision practices to detect and prevent
  circumvention of the reverse solicitation exemption under MiCA, ESMA35-1872330276-2030,
  26 February 2025, paragraphs 12, 13, 19, 23 and 24:
  <https://www.esma.europa.eu/sites/default/files/2025-02/ESMA35-1872330276-2030_Guidelines_on_reverse_solicitation_under_MiCA.pdf>
- ESMA, Final Report on the guidelines on reverse solicitation under MiCA, ESMA35-1872330276-1899,
  17 December 2024, paragraph 10:
  <https://www.esma.europa.eu/sites/default/files/2024-12/ESMA35-1872330276-1899_-_Final_report_on_GLs_on_reverse_solicitation_under_MiCA.pdf>
- ESMA, Consultation Paper on the draft guidelines on reverse solicitation under MiCA,
  ESMA35-1872330276-1619, 29 January 2024:
  <https://www.esma.europa.eu/sites/default/files/2024-01/ESMA35-1872330276-1619_Consultation_Paper_on_the_draft_guidelines_on_reverse_solicitation_under_MiCA.pdf>.
  Also searched for the negative in section 4, without a match: ESMA's statement of 17 October 2023
  (ESMA74-449133380-441), its supervisory briefing on the authorisation of crypto-asset service
  providers (ESMA75-453128700-1263), its statement on the end of the MiCA transitional periods
  (ESMA75-113276571-1631), and its Q&As 2125, 2143 and 2295.
- Directive 2014/65/EU (MiFID II), Article 4(1)(35), CELEX 32014L0065:
  <https://eur-lex.europa.eu/eli/dir/2014/65/oj>
- VARA, Virtual Assets and Related Activities Regulations 2023, Regulation II.C.1 and Schedule 4:
  <https://rulebooks.vara.ae/rulebook/c-prohibited-virtual-assets>,
  <https://rulebooks.vara.ae/rulebook/schedule-4-definitions>; consolidated version of 19 May 2025:
  <https://rulebooks.vara.ae/sites/default/files/en_net_file_store/VARA_EN_18_VER992_2.pdf>
- VARA, Marketing Regulations 2024, Marketing Regulations I.B.4 and I.B.5 and the introduction:
  <https://rulebooks.vara.ae/rulebook/b-general-prohibitions>,
  <https://rulebooks.vara.ae/rulebook/introduction-19>
- VARA, Virtual Asset Issuance Rulebook, 19 May 2025:
  <https://rulebooks.vara.ae/sites/default/files/en_net_file_store/VARA_EN_293_VER20250519.pdf>
- VARA, Compliance and Risk Management Rulebook, 19 May 2025, Rule III.D.5:
  <https://rulebooks.vara.ae/sites/default/files/en_net_file_store/VARA_EN_123_VER20250519.pdf>
- DFSA Rulebook, General Module (GEN), GEN 3A.1.1, GEN 3A.2.2 and its guidance, version in force
  since 12 January 2026: <https://dfsaen.thomsonreuters.com/rulebook/gen-3a11>,
  <https://dfsaen.thomsonreuters.com/rulebook/gen-3a22>,
  <https://dfsaen.thomsonreuters.com/rulebook/gen-3a22-guidance>
- In the repository: the [README](../../README.md), [specification.md](specification.md), and the
  tests and classes named in section 2.2.
