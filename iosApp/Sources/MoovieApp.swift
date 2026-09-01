import SwiftUI
import MoovieShared

/// Le délégué d'application, qui n'existe que pour l'orientation.
///
/// C'est le seul objet à qui iOS pose la question : `supportedInterfaceOrientationsFor`
/// est interrogé à chaque fois que le système envisage une rotation. Une vue ne
/// peut pas décider pour elle-même — elle ne peut que changer la réponse d'ici,
/// puis demander qu'on la repose.
///
/// SwiftUI n'installe pas de délégué par défaut ; `UIApplicationDelegateAdaptor`
/// en pose un sans rien changer au reste du cycle de vie.
final class MoovieAppDelegate: NSObject, UIApplicationDelegate {

    /// Portrait par défaut, comme sur le téléphone Android : les écrans de
    /// l'application sont des listes et des grilles, qu'un paysage étirerait
    /// sans rien montrer de plus.
    static var orientations: UIInterfaceOrientationMask = .portrait

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        MoovieAppDelegate.orientations
    }
}

/// Coque SwiftUI de l'application.
///
/// Tout ce que fait Swift ici, et tout ce qu'il doit faire : présenter le
/// `UIViewController` que rend Compose Multiplatform, et traduire les deux ou
/// trois demandes que Compose ne peut pas adresser au système lui-même.
/// L'interface est en Kotlin et partagée avec Android, la TV et le desktop —
/// dupliquer un écran en SwiftUI reviendrait à maintenir deux fois la même chose.
@main
struct MoovieApp: App {

    @UIApplicationDelegateAdaptor(MoovieAppDelegate.self) private var delegate

    init() {
        // Compose signale l'entrée et la sortie du lecteur ; la danse UIKit qui
        // s'ensuit reste ici, parce qu'elle diffère entre iOS 15 et iOS 16 et
        // qu'elle passe par le délégué ci-dessus. Voir `OrientationEcran`.
        OrientationEcran.shared.surChangement = {
            MoovieApp.appliquerOrientation(paysage: OrientationEcran.shared.paysageForce)
        }
    }

    var body: some Scene {
        WindowGroup {
            ComposeRoot()
                // Compose dessine sa propre barre de statut et retire lui-même
                // les encoches — voir le `windowInsetsPadding` de la racine.
                // Sans cela l'écran s'arrêterait sous la Dynamic Island et le
                // lecteur ne serait pas plein écran.
                .ignoresSafeArea(.all)
        }
    }

    /// Applique le masque, puis demande au système de reconsidérer la rotation.
    ///
    /// Changer le masque ne suffit pas : iOS ne le relit qu'à l'occasion d'un
    /// événement. `setNeedsUpdateOfSupportedInterfaceOrientations` est cet
    /// événement — sans lui, l'appareil reste dans l'orientation où il était et
    /// ne bascule qu'au prochain mouvement du poignet.
    ///
    /// Deux chemins, parce qu'Apple a changé le sien en iOS 16 :
    /// `requestGeometryUpdate` là où il existe, et le détour par le KVC de
    /// `UIDevice` en dessous — la cible de déploiement du projet est iOS 15.
    private static func appliquerOrientation(paysage: Bool) {
        let masque: UIInterfaceOrientationMask = paysage ? .landscape : .portrait
        MoovieAppDelegate.orientations = masque

        // Sur le fil principal : on touche à la hiérarchie de vues, et l'appel
        // vient du fil de composition de Compose.
        DispatchQueue.main.async {
            guard let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first else { return }

            if #available(iOS 16.0, *) {
                // Dans la branche, et non avant : cette méthode est elle-même
                // iOS 16. Son équivalent d'avant est
                // `attemptRotationToDeviceOrientation`, plus bas — les deux
                // disent la même chose au système, « relis le masque
                // maintenant », mais aucune n'existe des deux côtés.
                // Toutes les fenêtres de la scène, et non la première : sous
                // SwiftUI il peut y en avoir plus d'une — une modale, une
                // alerte — et c'est celle qui présente qui décide de
                // l'orientation. En interroger une seule laissait la demande
                // sans effet dès qu'une autre était devant.
                for fenetre in scene.windows {
                    fenetre.rootViewController?
                        .setNeedsUpdateOfSupportedInterfaceOrientations()
                }
                scene.requestGeometryUpdate(
                    .iOS(interfaceOrientations: masque)
                ) { _ in
                    // L'échec est sans conséquence : le masque reste posé, et
                    // l'appareil basculera au prochain mouvement. Rien à dire à
                    // l'utilisateur, rien à réessayer.
                }
            } else {
                let cible: UIInterfaceOrientation = paysage ? .landscapeRight : .portrait
                UIDevice.current.setValue(cible.rawValue, forKey: "orientation")
                UIViewController.attemptRotationToDeviceOrientation()
            }
        }
    }
}

/// Pont UIKit → SwiftUI.
///
/// `MoovieViewControllerKt` est le nom sous lequel Kotlin/Native exporte les
/// fonctions de premier niveau de `MoovieViewController.kt` : le nom du fichier
/// suffixé de `Kt`.
private struct ComposeRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MoovieViewControllerKt.MoovieViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
