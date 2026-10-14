# Правила ProGuard для библиотек WebP (TwelveMonkeys). Пути к jar подставляет задача shrinkWebp в build.gradle.

# Нужен только читатель WebP: всё, до чего нельзя дойти от его Spi, выбрасывается (~500 КБ -> ~90 КБ).
-keep,allowobfuscation class com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi { public <init>(); }
# Форматы метаданных ImageIO создаёт по имени класса, которое называет Spi.
-keep,allowobfuscation class * extends javax.imageio.metadata.IIOMetadataFormat { public static *** getInstance(); }

# Оставшиеся классы получают короткие имена и переезжают в пакет мода: так они не столкнутся с полной копией TwelveMonkeys из другого мода.
# Spi получает постоянное имя из webp.map - по нему его находит ClientHost.
-repackageclasses com.grihail.minepapers.lib
-applymapping webp.map
# На Windows a.class и A.class - один и тот же файл.
-dontusemixedcaseclassnames
# Имена классов в строках (Spi называет так свой читатель и форматы метаданных) переименовываются вместе с классами.
-adaptclassstrings
# Номера строк и имена переменных не нужны: стек внутри чужой библиотеки всё равно читают по mapping.txt.
-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions

# Оптимизация дала бы ещё ~4 КБ, но меняет код библиотеки - не стоит риска.
-dontoptimize
# Предупреждения о рефлексии внутри библиотеки (StringUtil, SystemUtil, PSD) к чтению WebP не относятся.
-dontnote com.twelvemonkeys.**
