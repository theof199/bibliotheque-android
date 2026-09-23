import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/** `local.properties` (ignoré par git) : l'adresse de l'API de développement. */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** `signing/release.properties` (ignoré par git), écrit par `bin/keystore`. */
val signingProperties = Properties().apply {
    val file = rootProject.file("signing/release.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "fr.mediatheque.journal"
    // On compile contre 36 parce que activity-compose 1.11.0 exige
    // `minCompileSdk=36` ; ce qu'on promet au téléphone reste `targetSdk 35`.
    compileSdk = 36
    // Sans cette ligne, AGP 8.13 réclame *sa* version par défaut, les
    // build-tools 35, que l'image n'a pas — et les retélécharge à chaque
    // `docker compose run --rm`, silencieusement, puisque le conteneur meurt
    // avec la commande. La valeur doit rester celle du `BUILD_TOOLS` de
    // Dockerfile.build.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "fr.mediatheque.journal"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val path = signingProperties.getProperty("keystore.path")
            if (path != null) {
                storeFile = rootProject.file(path)
                storePassword = signingProperties.getProperty("keystore.password")
                keyAlias = signingProperties.getProperty("key.alias")
                keyPassword = signingProperties.getProperty("key.password")
            }
        }
    }

    buildTypes {
        debug {
            // Les deux variantes cohabitent sur le même téléphone : signatures
            // différentes, donc deux applications plutôt qu'une réinstallation
            // qu'Android refuserait par-dessus l'autre.
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Journal (dev)")
            // L'instance locale, en HTTP : le network_security_config du
            // dossier `debug` l'autorise, et lui seul.
            val url = localProperties.getProperty("api.debug.url")
                ?: "http://10.0.2.2:3000" // l'hôte vu depuis un émulateur, si jamais
            buildConfigField("String", "API_BASE_URL", "\"$url\"")
        }
        release {
            resValue("string", "app_name", "Journal")
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "API_BASE_URL", "\"https://mini-mediatheque.fr/api\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// La construction `release` sans clé doit échouer en le disant, pas en
// produisant un APK que le téléphone refusera, ni sur le message technique
// d'AGP (« SigningConfig "release" is missing required property "storeFile" »)
// une fois toute la compilation faite.
//
// Trois pièges, tous trois constatés ici :
//
// 1. Il n'y a pas de `validateSigningRelease` où s'accrocher. AGP ne crée
//    `validateSigning<Variante>` que pour une configuration qui porte déjà un
//    `storeFile` — donc jamais dans le cas qu'on veut attraper. `./gradlew
//    :app:tasks --all` ne liste que `validateSigningDebug`.
// 2. Un `doFirst` posé sur une tâche d'AGP ne s'exécute pas quand cette tâche
//    est `UP-TO-DATE`. Le garde se tairait dès la deuxième tentative, c'est-à-
//    dire précisément quand on a cessé de le surveiller. D'où une tâche à
//    nous, sans sortie déclarée, donc jamais à jour.
// 3. `doLast { check(uneValeurDuScript) }` fait référence au script lui-même,
//    que le cache de configuration ne sait pas sérialiser. La valeur se
//    recopie dans une variable locale au bloc de configuration ; le corps de
//    la tâche ne voit plus qu'un booléen.
//
// `preReleaseBuild` est la première tâche de la variante `release` : s'y
// accrocher fait échouer en quelques secondes, avant la compilation. La
// variante `debug` ne la traverse pas.
val cleDeSignatureDeclaree = signingProperties.getProperty("keystore.path") != null

val verifierLaCleDeSignature = tasks.register("verifierLaCleDeSignature") {
    val declaree = cleDeSignatureDeclaree
    doLast {
        check(declaree) {
            "Aucune clé de signature : lance bin/keystore une fois (voir README)."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifierLaCleDeSignature)
}

dependencies {
    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    // `okhttp3.CookieJar` est importé directement (SessionCookieJar.kt, ApiClient.kt) : sans
    // cette ligne, la dépendance n'existait qu'en transitif via ktor-client-okhttp, jamais
    // déclarée (revue de la vague finale, mineur 11). Pas de version : celle que Ktor résout.
    implementation("com.squareup.okhttp3:okhttp")
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // Les animations Lottie des célébrations (brief du 23 septembre 2026, soir) : les JSON vivent
    // dans `app/src/main/assets/lottie/`, jamais téléchargés (README, « Les animations »).
    implementation(libs.lottie.compose)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
