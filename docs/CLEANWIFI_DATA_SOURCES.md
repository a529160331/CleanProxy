# CleanWiFi public Wi-Fi data-source assessment

Updated: 2026-11-07

## Product requirement

CleanWiFi's primary value is matching nearby venue/guest Wi-Fi networks against credentials that the venue or an authorized contributor has explicitly permitted to be shared. Scanning is only the discovery input; it is not the product outcome.

The app must never present a guessed, leaked, scraped, or unverified password as a usable credential. Do not attempt to break WPA/WPA2/WPA3 authentication or bypass access controls.

## Candidate source review (rechecked 2026-11-07)

| Candidate | What is publicly documented | Integration decision |
| --- | --- | --- |
| WifiPass (wifipass.io) | Its website claims 30M+ hotspots and 50M+ working passwords, crowdsourced from travelers, and says its API uses Firebase App Check to serve the official app rather than scrapers. | High-value lead, but no public developer API or redistribution grant is documented. Do not reverse-engineer its app API, scrape, or copy its data. Contact the operator for a licensed partner API or export. |
| WiFiShare (wifishare.github.io) | The current project page explicitly says the API is not live, no data is collected, and the project is still at specification/foundation stage. Its planned public data is ODbL 1.0; API v1 describes area lookups, but no live dataset is available yet. | Monitor and prepare a compatible importer, but it cannot supply passwords today. The project explicitly says protected networks are included only when their owner deliberately shares them. |
| OpenWifiPassMap (github.com/oriolj/openwifipassmap) | A public crowdsourced venue Wi-Fi directory with an API, but the repository still marks its license as TBD. | Do not import code or records until a clear license and data-submission permission are published. |
| Freifunk OpenWiFiMap (github.com/freifunk/openwifimap-api) | MIT-licensed API/database for free community networks such as Freifunk; not a password-sharing directory. | Potentially useful as a separate source of open-network discovery, not as a source of protected guest passwords. Check the live endpoint and current service terms before use. |
| OpenWifiPass (seemoo-lab/openwifipass) | Experimental GPL-3.0 implementation of Apple's Wi-Fi password-sharing protocol, requiring a Linux host with BLE; README warns it is untested/incomplete and does not authenticate requestors. | Not a public password database and not suitable to embed in CleanWiFi. Do not use it to solicit or disclose credentials from nearby devices. |

## Current repository source

The current app reads:

- `wifiapp/src/main/assets/public-wifi.json`
- `PublicWifiDatabase.kt` fetches the same file from the development branch's raw GitHub URL.
- Only records with `status = "approved"` and `permissionConfirmed = true` are returned.
- SSID matching is exact; this is intentional because SSIDs are case-sensitive.
- The committed JSON currently has an empty `hotspots` array. Therefore there are currently zero shared credentials available to match.

## Admission requirements for any new source

Before adding a provider, verify and record all of the following:

1. A documented API or downloadable dataset actually exists and is reachable.
2. The provider's terms explicitly permit the app's intended access, caching, and display of credentials.
3. The contributor or venue has authority to share each guest credential publicly.
4. The record has provenance, venue/city context, a last-updated timestamp, and a review/verification state.
5. Removal and correction requests can be honored.
6. The source does not require scraping a consumer app, bypassing authentication, or rehosting data against its terms.

## Planned integration shape

Keep provider integrations behind a small adapter that normalizes records into the existing `Hotspot` model. Each provider must be independently enabled only after its API and license checks pass. Keep the first-party moderated JSON feed as a fallback. Merge only explicitly approved records, deduplicate conservatively, and preserve provider attribution.

## Immediate next steps

1. Seek a licensed partner/API arrangement from WifiPass; its own anti-scraping design means unofficial API reuse is not an acceptable shortcut.
2. Keep collecting venue-authorized submissions through the existing GitHub issue form.
3. Monitor WiFiShare until its API and real dataset are actually live; its current specification is useful for future interoperability but cannot return passwords today.
4. Optionally add Freifunk/OpenWiFiMap as a clearly separate open-network source after checking the live API terms; never label it as a password source.
5. Add a provider adapter only after a real, permitted endpoint is confirmed.
6. Test returned credentials at the venue or through an authorized verification workflow; never claim a password is working solely because it exists in a dataset.

## Important limitation

A database of Wi-Fi network names or access-point locations is not a password database. Open-network maps and wardriving datasets must not be represented as sources of guest passwords.

## China-mainland source review (2026-11-07)

The product's primary market is mainland China. The following local leads have been checked against public documentation; none currently provides a confirmed, openly licensed nationwide password API.

| China source / lead | Verified facts | Decision |
| --- | --- | --- |
| WiFi万能钥匙 / 南京尚网网络 | Its official help page says it has more than ten million hotspot records and describes user-shared hotspots. Its hotspot-sharing statement says records come from partners or users, and contributors must have the right to share. Its current service agreement says users submitting a shared hotspot submit the network name and password, and must own the network or have sufficient authorization. | **Top partnership target.** The public materials establish that a credential-sharing ecosystem exists, but do not document a public third-party password API or redistribution license. Do not scrape, reverse-engineer private endpoints, or copy app data. Ask for a licensed partner API/data agreement. Public business contacts shown on its official site include wifisales@zenmen.com; another official business contact is business@wifi.com. |
| Shenzhen Government Open Data: public-WiFi venue list | Dataset “深圳市工信局公共WIFI的热点场所信息清单开放专用” is listed as unconditional open data, updated 2026-04-20, with 94 sample records. Its description says it contains venue name, hotspot type, address/group address and district, not passwords; full data requires an information-disclosure application. | Useful **venue/location seed data**, not a credential source. Only ingest fields and use cases allowed by the dataset terms; do not infer or manufacture passwords. |
| Juhe Data “全国WIFI” API | The product page describes nationwide free-Wi-Fi hotspot distribution, but currently labels the service “维护中” (under maintenance). The page describes hotspot information, not a verified password dataset. | Not currently usable; do not integrate or pay until the provider confirms service availability, data fields, provenance, and rights to display any credentials. |
| 360免费WiFi (historical documentation) | A 360 privacy whitepaper describes a user feature for sharing a user's own hotspot name/password to its server for nearby users. The document is old and does not establish a current public API or third-party redistribution rights. | Historical evidence of another sharing model, **not** a current source. Seek current operator confirmation before considering any partnership. |
| Public-sector open Wi-Fi networks | Government/public venue datasets and unified-authentication portals may list public access locations. The Shenzhen dataset above is a concrete example. | Treat as a separate “public hotspot discovery” feed. A listed venue or SSID does not imply a password is public; many networks use captive portals, phone/SMS authentication, or managed access. |

### Mainland-China compliance and admission rules

- The Ministry of Public Security's 2018 notice on Wi-Fi-sharing services states that personal Wi-Fi networks and non-public internal networks of government agencies/enterprises must not be shared without the person's or organization's authorization/consent. This is a hard admission rule for CleanWiFi.
- Only accept venue guest Wi-Fi credentials when the venue/operator has explicitly authorized public sharing, or when the contributor can demonstrate authority to share that network.
- Exclude residential/private networks, enterprise/internal networks, school dormitory networks, and credentials obtained from leaks, scraping, device extraction, or unauthorized access.
- A public venue listing is not proof that its password may be redistributed. Keep venue metadata and credential records as separate data types.
- Every credential record should carry source/provenance, authorization evidence or attestation, SSID, BSSID only where lawfully and appropriately supplied, venue/location, last verified time, verification status, and a removal contact/process. Avoid collecting contributor identity or device identifiers unless strictly necessary and legally reviewed.

### Mainland-China next actions

1. Prepare a concise partnership request to WiFi万能钥匙 asking whether it offers a third-party licensed API, partner SDK, or permitted data export for public/partner guest hotspots, and what attribution, caching, and security requirements apply.
2. Use Shenzhen's 94-record public-Wi-Fi venue dataset as a **location-only pilot** after downloading and checking its field-level metadata and reuse terms. Do not show passwords for those records unless separately authorized.
3. Contact Juhe only to confirm whether its “全国WIFI” product is still maintained and whether it contains credentials at all; current public page says it is under maintenance.
4. Keep the first-party moderated credential feed as the only immediately controllable password source. Recruit venues directly and require an explicit authorization checkbox plus a removal path.
5. Do not claim “全国密码库已接入” until a real source returns credential records and the app has passed live verification tests in multiple mainland cities.

### Important source-rights clarification (2026-11-07)

A closer review of the current WiFi万能钥匙 software agreement (effective 2026-09-10) materially narrows the partnership path:

- The agreement expressly identifies Wi-Fi hotspot names, coordinates, and passwords as protected intellectual-property / business-secret content, and says rights remain with the operator or its licensors.
- It prohibits uses not expressly authorized in writing.
- Its sharing clause permits the platform's own users/technologies to connect to user-shared hotspots; it does **not** grant CleanWiFi a right to extract, display, copy, or redistribute those records.
- Therefore the only acceptable route is a written license / partner API agreement explicitly covering third-party app access, credential display or connection handoff, caching, attribution, security controls, user consent, and deletion/withdrawal. No scraping, reverse engineering, client automation, or reuse of the consumer app's private endpoints.

The official Shenzhen Open Data Platform terms also require source attribution for app outputs but prohibit transferring acquired data resources (whether paid or free) to others; the platform may also require removal when data is taken offline. Its public Wi-Fi dataset is described as a 94-record sample with venue name/type/address/district, not passwords, and the full dataset requires an information-disclosure application. **Do not mirror the raw dataset into CleanWiFi's hosted JSON feed or redistribute it** unless the platform gives written permission for that exact use. First request written clarification on whether an app may display derived venue-location results without transferring the underlying dataset; if not, keep the dataset out of the app.

Verified official references:
- WiFi万能钥匙 current software agreement: https://static.ttwifi.net/policy_wikey.html
- WiFi万能钥匙 hotspot mutual-sharing statement: https://cn.wifi.com/app_h5/connect_sdk/share_plan/
- WiFi万能钥匙 business cooperation page: https://wifi.com/wifipro/professional.html
- Shenzhen Open Data dataset: https://opendata.sz.gov.cn/data/dataSet/toDataDetails/29200_00603562
- Shenzhen Open Data platform terms are displayed on that dataset page.

### Contact / outreach status

The official WiFi万能钥匙 business page publishes wifisales@zenmen.com and the business WeChat ID WiFiPartner. This is a lead only; no message has been sent and no partnership/API access has been granted.

