[//]: # (NOTE: Please put every sentence in its own line, Transifex puts every line in its own translation field!)

## 4.1

  * Улучшено определение писем и другого контента при открытии


## 4.0

  * Эксперементальная поддержка для токена безопасности через USB
  * Добавлена возможность изменения пароля отделённых ключей


## 3.9

  * Идентификация и обработка текстовых данных
  * Улучшена производительность
  * Улучшения пользовательского интерфейса работы с токенами безопасности


## 3.8

  * Изменение дизайна окна редактирования ключей
  * Choose remember time individually when entering passwords
  * Импорт ключа с Facebook


## 3.7

  * Улучшенная поддержка Android 6 (права доступа, интеграция в выделенный текст)
  * API: Версия 10


## 3.6

  * Зашифрованные резервные копии
  * Исправление ошибок, выявленных внешним аудитом безопасности
  * Диалог создания ключа YubiKey NEO 
  * Основная поддержка MIME
  * Автоматическая синхронизация ключа
  * Experimental feature: link keys to Github, Twitter accounts
  * Experimental feature: key confirmation via phrases
  * Экспериментальная функция: тёмная тема
  * API: Версия 9


## 3.5

  * Аннулирование ключа при его удалении
  * Улучшенная проверка небезопасной криптографии
  * Исправлено: Не закрывался диалог первичной настройки
  API: Версия 8


## 3.4

  * Анонимное скачивание ключей через Tor
  * Поддержка прокси
  Улучшенная обработка ошибок YubiKey


## 3.3

  * Новое окно расшифровки
  * Расшифровка нескольких файлов одновременно
  * Улучшена обработка ошибок YubiKey


## 3.2

  * Первая версия с поддержкой YubiKey в программе: Изменение ключа, привязка ключа к брелоку,...
  * Материальный дизайн
  * Интеграция сканера кодов QR (Требуются новые полномочия)
  * Улучшение мастера создания ключей
  * Исправлено пропадание контактов после синхронизации
  * Требуется Android 4
  * Изменение дизайна окна ключей
  * Simplify crypto preferences, better selection of secure ciphers
  * API: Detached signatures, free selection of signing key,...
  * Fix: Some valid keys were shown revoked or expired
  * Don't accept signatures by expired or revoked subkeys
  * Keybase.io support in advanced view
  * Method to update all keys at once


## 3.1.2

  * Fix key export to files (now for real)


## 3.1.1

  * Fix key export to files (they were written partially)
  Исправление падений на Android 2.3


## 3.1

  Исправление падений на Android 5
  Новое окно подтверждения
  * Secure Exchange directly from key list (SafeSlinger library)
  * New QR Code program flow
  * Изменение дизайна диалога расшифровки
  Использование новых иконок и цевтов
  * Fix import of secret keys from Symantec Encryption Desktop
  * Experimental YubiKey support: Subkey IDs are now checked correctly


## 3.0.1

  * Better handling of large key imports
  * Improved subkey selection


## 3.0

  * Propose installable compatible apps in apps list
  * New design for decryption screens
  * Many fixes for key import, also fixes stripped keys
  * Honor and display key authenticate flags
  Пользовательский интерфейс для генерации пользовательских ключей
  * Исправление сертификатов отзыва
  Новый облачный поиск (по традиционным серверам ключей и по keybase.io)
  * Support for stripping keys inside OpenKeychain
  Экспериментальная поддержка YubiKey: Поддержка генерации и расшифровка подписей


## 2.9.2

  * Исправление ключей, сломанных в 2.9.1
  * Экспериментальная поддержка Yubikey: Расшифровка теперь работает через API


## 2.9.1

  * Split encrypt screen into two
  * Fix key flags handling (now supporting Mailvelope 0.7 keys)
  * Improved passphrase handling
  * Key sharing via SafeSlinger
  * Experimental YubiKey support: Preference to allow other PINs, currently only signing via the OpenPGP API works, not inside of OpenKeychain
  * Fix usage of stripped keys
  SHA256 по умолчанию для совместимости
  * Intent API has changed, see https://github.com/open-keychain/open-keychain/wiki/Intent-API
  * OpenPGP API now handles revoked/expired keys and returns all user ids


## 2.9

  * Fixing crashes introduced in v2.8
  * Experimental ECC support
  * Experimental YubiKey support: Only signing with imported keys


## 2.8

  * So many bugs have been fixed in this release that we focus on the main new features
  * Key edit: awesome new design, key revocation
  * Key import: awesome new design, secure keyserver connections via hkps, keyserver resolving via DNS SRV records
  * New first time screen
  * New key creation screen: autocompletion of name and email based on your personal Android accounts
  * File encryption: awesome new design, support for encrypting multiple files
  * New icons to show status of key (by Brennan Novak)
  * Important bug fix: Importing of large key collections from a file is now possible
  * Notification showing cached passphrases
  * Keys are connected to Android's contacts

This release wouldn't be possible without the work of Vincent Breitmoser (GSoC 2014), mar-v-in (GSoC 2014), Daniel Albert, Art O Cathain, Daniel Haß, Tim Bray, Thialfihar

## 2.7

  * Purple! (Dominik, Vincent)
  * New key view design (Dominik, Vincent)
  * New flat Android buttons (Dominik, Vincent)
  * API fixes (Dominik)
  * Keybase.io import (Tim Bray)


## 2.6.1

  * Some fixes for regression bugs


## 2.6

  * Key certifications (thanks to Vincent Breitmoser)
  * Support for GnuPG partial secret keys (thanks to Vincent Breitmoser)
  * New design for signature verification
  * Custom key length (thanks to Greg Witczak)
  * Fix share-functionality from other apps


## 2.5

  * Fix decryption of symmetric OpenPGP messages/files
  * Refactored key edit screen (thanks to Ash Hughes)
  * New modern design for encrypt/decrypt screens
  * OpenPGP API version 3 (multiple api accounts, internal fixes, key lookup)


## 2.4
Thanks to all applicants of Google Summer of Code 2014 who made this release feature rich and bug free!
Besides several small patches, a notable number of patches are made by the following people (in alphabetical order):
Daniel Hammann, Daniel Haß, Greg Witczak, Miroojin Bakshi, Nikhil Peter Raj, Paul Sarbinowski, Sreeram Boyapati, Vincent Breitmoser.

  * New unified key list
  * Colorized key fingerprint
  * Support for keyserver ports
  * Deactivate possibility to generate weak keys
  * Much more internal work on the API
  * Certify user ids
  * Keyserver query based on machine-readable output
  * Lock navigation drawer on tablets
  * Suggestions for emails on creation of keys
  * Поиск в списках публичных ключей
  * And much more improvements and fixes…


## 2.3.1

  * Hotfix for crash when upgrading from old versions


## 2.3

  * Remove unnecessary export of public keys when exporting secret key (thanks to Ash Hughes)
  * Fix setting expiry dates on keys (thanks to Ash Hughes)
  * More internal fixes when editing keys (thanks to Ash Hughes)
  * Querying keyservers directly from the import screen
  * Fix layout and dialog style on Android 2.2-3.0
  * Fix crash on keys with empty user ids
  * Fix crash and empty lists when coming back from signing screen
  * Bouncy Castle (cryptography library) updated from 1.47 to 1.50 and build from source
  * Fix upload of key from signing screen


## 2.2

  * New design with navigation drawer
  * Новый дизайн списка публичных ключей
  * Новый вид просмотра публичного ключа
  * Bug fixes for importing of keys
  * Кросс-сертификация ключей (спасибо Ash Hughes)
  * Handle UTF-8 passwords properly (thanks to Ash Hughes)
  * Первая версия с новыми языками (спасибо переводчикам на Transifex)
  * Передача ключей через QR коды исправлена и улучшена 
  * Проверка подписи пакета для API


## 2.1.1

  * Обновление API, подготовка к интеграции с K-9 Mail


## 2.1

  * Множество исправлений ошибок
  * Новый API для разработчиков
  * PRNG bug fix by Google


## 2.0

  * Переработка дизайна
  * Передачи публичных ключей через QR коды и NFC
  * Подпись ключей
  * Загрузка ключей на сервер
  * Исправление проблем импорта
  * New AIDL API


## 1.0.8

  * Основная поддержка сервера ключей
  * App2sd
  * More choices for passphrase cache: 1, 2, 4, 8, hours
  * Translations: Norwegian Bokmål (thanks, Sander Danielsen), Chinese (thanks, Zhang Fredrick)
  * Исправления ошибок
  * Оптимизации


## 1.0.7

  * Fixed problem with signature verification of texts with trailing newline
  * More options for passphrase cache time to live (20, 40, 60 mins)


## 1.0.6

  * Account adding crash on Froyo fixed
  * Безопасное удаление файла
  * Option to delete key file after import
  * Stream encryption/decryption (gallery, etc.)
  * New options (language, force v3 signatures)
  * Изменения интерфейса
  * Исправления ошибок


## 1.0.5

  * Немецкий и итальянский переводы
  * Much smaller package, due to reduced BC sources
  * Новый интерфейс настроек
  * Layout adjustment for localization
  * Signature bugfix


## 1.0.4

  * Fixed another crash caused by some SDK bug with query builder


## 1.0.3

  * Fixed crashes during encryption/signing and possibly key export


## 1.0.2

  * Filterable key lists
  * Smarter pre-selection of encryption keys
  * New Intent handling for VIEW and SEND, allows files to be encrypted/decrypted out of file managers
  * Fixes and additional features (key preselection) for K-9 Mail, new beta build available


## 1.0.1

  * GMail account listing was broken in 1.0.0, fixed again


## 1.0.0

  * K-9 Mail интеграция, APG поддерживает beta-версию K-9 Mail
  * Поддержка большего количества файловых менеджеров (включая ASTRO)
  * Словенский перевод
  Новая база данных, намного быстрее и компактнее
  * Defined Intents and content provider for other apps
  * Исправления ошибок