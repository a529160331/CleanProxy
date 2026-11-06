# CleanWiFi public Wi-Fi data-source assessment

Updated: 2026-11-07

## Product requirement

CleanWiFi's primary value is matching nearby venue/guest Wi-Fi networks against credentials that the venue or an authorized contributor has explicitly permitted to be shared. Scanning is only the discovery input; it is not the product outcome.

The app must never present a guessed, leaked, scraped, or unverified password as a usable credential. Do not attempt to break WPA/WPA2/WPA3 authentication or bypass access controls.

## Candidate source review

| Candidate | What is publicly documented | Integration decision |
| --- | --- | --- |
| WifiPass (wifipass.io) | Its website describes a crowdsourced database and claims 30M+ hotspots / 50M+ working passwords. | Do not scrape or copy its data. No public developer API or redistribution grant has been verified. Revisit only if the operator provides an API and written terms permitting this use. |
| WiFiShare (wifishare.github.io) | Open specification and planned API/data dump; its own status page says the API is not live and no data is being collected yet. | Monitor; not a live password source today. Respect the spec's privacy model and ODbL data terms if it becomes operational. |
| OpenWifiPassMap (github.com/oriolj/openwifipassmap) | Public crowdsourced venue Wi-Fi directory project. Its repository currently says the license is TBD. | Do not import code or records until a clear license and data-submission permission are published. |
| Freifunk OpenWiFiMap (github.com/freifunk/openwifimap-api) | Open directory/API focused on free/open networks. | Potential source for open-network discovery, not a verified source of protected-network passwords. Check live API terms and endpoint availability before use. |

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

1. Contact candidate operators or inspect their official API/terms for a written integration path.
2. Keep collecting venue-authorized submissions through the existing GitHub issue form.
3. Add a provider adapter only after a real, permitted endpoint is confirmed.
4. Test returned credentials at the venue or through an authorized verification workflow; never claim a password is working solely because it exists in a dataset.

## Important limitation

A database of Wi-Fi network names or access-point locations is not a password database. Open-network maps and wardriving datasets must not be represented as sources of guest passwords.
