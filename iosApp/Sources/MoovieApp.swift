import SwiftUI
import MoovieShared

/// Coque SwiftUI de l'application.
///
/// Tout ce que fait Swift ici, et tout ce qu'il doit faire : présenter le
/// `UIViewController` que rend Compose Multiplatform. L'interface elle-même est
/// en Kotlin et partagée avec Android, la TV et le desktop — dupliquer un écran
/// en SwiftUI reviendrait à maintenir deux fois la même chose.
@main
struct MoovieApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRoot()
                // Compose dessine sa propre barre de statut et gère lui-même
                // les encoches ; sans cela l'écran s'arrête sous la Dynamic
                // Island et le lecteur ne serait pas plein écran.
                .ignoresSafeArea(.all)
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
