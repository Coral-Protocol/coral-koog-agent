# Changelog

## [1.3.0](https://github.com/Coral-Protocol/coral-koog-agent/compare/v1.2.1...v1.3.0) (2026-10-12)


### Features

* add hydrate task for easier setup ([0b84f6a](https://github.com/Coral-Protocol/coral-koog-agent/commit/0b84f6afdff4ab5cc4143274744966f025cbfd9d))
* add tunneling support ([1911f7e](https://github.com/Coral-Protocol/coral-koog-agent/commit/1911f7e74e2056a46398a1eb2d44db87f3df8bc9))
* bump to 1.2.1 ([e7bb71a](https://github.com/Coral-Protocol/coral-koog-agent/commit/e7bb71abcb2e09358ee7f0008f83758117becceb))
* checkout tag ([601a522](https://github.com/Coral-Protocol/coral-koog-agent/commit/601a5221c7fea40b3cce592b0e1629f5e1079528))
* Improve build and template ergonomics ([f364189](https://github.com/Coral-Protocol/coral-koog-agent/commit/f36418930eba8be55a603f2857a55553169c4143))
* increase koog version to 0.6.4 ([1e4c032](https://github.com/Coral-Protocol/coral-koog-agent/commit/1e4c032dc3d7ef7949831824b07720f2d41532be))
* npm create and readme ([cacc53e](https://github.com/Coral-Protocol/coral-koog-agent/commit/cacc53ec095c2167762d4aacec5eb577de15dbe1))
* post hydrate readme prependix ([605df61](https://github.com/Coral-Protocol/coral-koog-agent/commit/605df6148b85407d02f9567132450b7f123fece1))
* quick-session script ([ee6f338](https://github.com/Coral-Protocol/coral-koog-agent/commit/ee6f33890edc13a843062d598cbd76e923ebea88))
* release convenience ([906b0ad](https://github.com/Coral-Protocol/coral-koog-agent/commit/906b0adc7e2ee9fea321a58e7635478807a2d717))
* self-remove hydration code ([ebc9832](https://github.com/Coral-Protocol/coral-koog-agent/commit/ebc9832b2ed69e9198e0e240ebceaa2a975a5e81))
* update for 1.4.x-1.5.0 ([e140ff3](https://github.com/Coral-Protocol/coral-koog-agent/commit/e140ff34130105583b2ee31ef143af523acf3383))
* Update to work with koog 0.5.2 via PatchedSseClientTransport.kt ([da75e1b](https://github.com/Coral-Protocol/coral-koog-agent/commit/da75e1b1fb4c4fda89e90ee8bc6724736e989ed0))
* use coral llm proxy ([840c127](https://github.com/Coral-Protocol/coral-koog-agent/commit/840c127386b96768f8b6be54e6af8507d1e3f1fe))
* version bump ([65fba0a](https://github.com/Coral-Protocol/coral-koog-agent/commit/65fba0a288c7c937389ff29658161eab71e39c94))


### Bug Fixes

* ./gradlew logging interfering with cli experience ([07797c6](https://github.com/Coral-Protocol/coral-koog-agent/commit/07797c6cc4dc5104298503aec2c9eb45b884a46d))
* add url ([3bad225](https://github.com/Coral-Protocol/coral-koog-agent/commit/3bad225b50c7a5cb9232a24f29d885ee83033e6e))
* bring back prependix ([1e80096](https://github.com/Coral-Protocol/coral-koog-agent/commit/1e800966b6dceaa391ab16e93e952dd3b3890d5b))
* bugs in convenience scripts ([a560ec9](https://github.com/Coral-Protocol/coral-koog-agent/commit/a560ec960453681b87ee561e8aaa45b2cc072b96))
* consistent naming ([632194f](https://github.com/Coral-Protocol/coral-koog-agent/commit/632194f7a94a90bb20710a7bd45ad47a3006689a))
* consistent naming ([de05e6d](https://github.com/Coral-Protocol/coral-koog-agent/commit/de05e6d386564b30598f0af83407282939e85e88))
* default to openai provider if not specified ([f12f20d](https://github.com/Coral-Protocol/coral-koog-agent/commit/f12f20dff4ee18bbd1730efd8274dd9f0b5f9aa3))
* fix graalvm error, git branch main, readme ([e6f90e6](https://github.com/Coral-Protocol/coral-koog-agent/commit/e6f90e6b188aeeab8c1290b0802fd7bc79207556))
* graalvm agent on by default ([f922b4d](https://github.com/Coral-Protocol/coral-koog-agent/commit/f922b4d9a6daabec27385c168f1247fd547d6d16))
* hard to follow slop in hydrate template task ([285e010](https://github.com/Coral-Protocol/coral-koog-agent/commit/285e0109e52ca871a38f069b0e27703325f476ed))
* misc release and qol fixes ([9c8f01f](https://github.com/Coral-Protocol/coral-koog-agent/commit/9c8f01f3f403ddb0ce78f4836f4b3ffeeb53fdb4))
* native builds ([17f016b](https://github.com/Coral-Protocol/coral-koog-agent/commit/17f016bc39a755079a78a07da3dd16e415a1db32))
* npm create ([c7dd7dd](https://github.com/Coral-Protocol/coral-koog-agent/commit/c7dd7dddeaeb00866373f630a60c029cfeadaaec))
* only save env values that are options or begin with CORAL_ in dev env utils ([1bca9de](https://github.com/Coral-Protocol/coral-koog-agent/commit/1bca9dec793768e2971ecac5d4ce50a98c0f7562))
* prevent gradle execution updates obscuring stdin questions ([d5a465c](https://github.com/Coral-Protocol/coral-koog-agent/commit/d5a465ca9167f7e46bacb38cdd25d112db1bddf7))
* prompt user whether to keep workflows ([e6e39b7](https://github.com/Coral-Protocol/coral-koog-agent/commit/e6e39b7dfbcd54adc43cab29c049dcbf5c64e1cf))
* remove misleading comment ([c366ad5](https://github.com/Coral-Protocol/coral-koog-agent/commit/c366ad523cb462bc804c2ba69de61aaf24cd41be))
* remove outdated cli suggestion ([3bafd80](https://github.com/Coral-Protocol/coral-koog-agent/commit/3bafd80236747168b792eb56e47f914f3d21aa97))
* remove remote post hydration again ([767604a](https://github.com/Coral-Protocol/coral-koog-agent/commit/767604aa96dfa4f43a78503ca0add724da90dfa7))
* remove tunnel functionality ([bba3aa3](https://github.com/Coral-Protocol/coral-koog-agent/commit/bba3aa3ac174eef126bbed7f1234b891da1b1e3b))
* tag increment ([b799d33](https://github.com/Coral-Protocol/coral-koog-agent/commit/b799d33aba0cd18a7880874c106cb5c59f090900))
* tag increment ([849391c](https://github.com/Coral-Protocol/coral-koog-agent/commit/849391c06b6004ebb1590e663de31c5d2bf9eddd))
* update dependency versions ([3fdbf9c](https://github.com/Coral-Protocol/coral-koog-agent/commit/3fdbf9c5354eab6c75d011f57996e629f4730797))
* update metadata ([c770318](https://github.com/Coral-Protocol/coral-koog-agent/commit/c770318922cc72e3c46d58a415645d0375913806))
* update proxy format ([1cc61ad](https://github.com/Coral-Protocol/coral-koog-agent/commit/1cc61adba670a50fe033c9fe33ca6fe123f001d0))
* updated valid reachability-metadata.json ([f352ef7](https://github.com/Coral-Protocol/coral-koog-agent/commit/f352ef7e4085691acacceebb8aca72452a28e0fd))
* Use new kotlin time Clock ([607e5ef](https://github.com/Coral-Protocol/coral-koog-agent/commit/607e5efb82b2d86264c9190810fa0c25689861ba))
* version bump ([22870db](https://github.com/Coral-Protocol/coral-koog-agent/commit/22870db7864d174de97b22e64e007f3eed5bb3e8))
