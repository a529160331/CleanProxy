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
