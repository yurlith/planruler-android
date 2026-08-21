package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.EditorAction3D
import com.planruler.fabrication3d.EngineQuota3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.ParameterRule3D
import com.planruler.fabrication3d.RouteFailureCode3D
import com.planruler.model.AppLanguage

/**
 * Turns the engine's typed refusals into shop language. The engine never ships prose,
 * so every refusal a fitter reads is translated here into the app's six languages.
 */
internal class Fabrication3DMessages(private val language: AppLanguage) {
    private fun t(pl: String, en: String, de: String, fr: String, it: String, ru: String) = when (language) {
        AppLanguage.POLISH -> pl
        AppLanguage.ENGLISH -> en
        AppLanguage.GERMAN -> de
        AppLanguage.FRENCH -> fr
        AppLanguage.ITALIAN -> it
        AppLanguage.RUSSIAN -> ru
    }

    fun of(error: Fabrication3DError): String = when (error) {
        is Fabrication3DError.InvalidParameter -> "${error.parameter}: ${rule(error.rule)}" +
            (error.limit?.let { " (${limitLabel()} $it)" } ?: "")

        is Fabrication3DError.UnknownPart -> t(
            "Nie znaleziono elementu ${error.partId}",
            "Part ${error.partId} was not found",
            "Bauteil ${error.partId} nicht gefunden",
            "Pièce ${error.partId} introuvable",
            "Componente ${error.partId} non trovato",
            "Деталь ${error.partId} не найдена",
        )

        is Fabrication3DError.UnknownPort -> t(
            "Nie znaleziono końcówki ${error.reference}",
            "Port ${error.reference} was not found",
            "Anschluss ${error.reference} nicht gefunden",
            "Port ${error.reference} introuvable",
            "Porta ${error.reference} non trovata",
            "Порт ${error.reference} не найден",
        )

        is Fabrication3DError.PortNotFree -> t(
            "Końcówka ${error.reference} jest już zespawana",
            "Port ${error.reference} is already welded",
            "Anschluss ${error.reference} ist bereits verschweißt",
            "Le port ${error.reference} est déjà soudé",
            "La porta ${error.reference} è già saldata",
            "Порт ${error.reference} уже сварен",
        )

        is Fabrication3DError.PortsIncompatible -> "${error.anchor}: ${rule(error.rule)}"

        is Fabrication3DError.AssemblyInvalid -> t(
            "Model nie domyka się: ${error.issues.size} usterek",
            "The model does not close: ${error.issues.size} issues",
            "Das Modell schließt nicht: ${error.issues.size} Fehler",
            "Le modèle ne se ferme pas : ${error.issues.size} défauts",
            "Il modello non si chiude: ${error.issues.size} problemi",
            "Модель не замыкается: ${error.issues.size} нарушений",
        )

        is Fabrication3DError.QuotaExceeded -> "${quota(error.quota)}: ${error.requested} > ${error.limit}"

        is Fabrication3DError.RouteNotFound -> route(error.code)

        is Fabrication3DError.NothingToDo -> action(error.action)

        is Fabrication3DError.Internal -> t(
            "Błąd wewnętrzny silnika (${error.stage})",
            "Internal engine error (${error.stage})",
            "Interner Fehler der Engine (${error.stage})",
            "Erreur interne du moteur (${error.stage})",
            "Errore interno del motore (${error.stage})",
            "Внутренняя ошибка движка (${error.stage})",
        )
    }

    private fun limitLabel() = t("limit", "limit", "Grenze", "limite", "limite", "предел")

    private fun rule(rule: ParameterRule3D): String = when (rule) {
        ParameterRule3D.NOT_FINITE, ParameterRule3D.NOT_A_NUMBER -> t(
            "wartość nie jest liczbą",
            "the value is not a number",
            "der Wert ist keine Zahl",
            "la valeur n'est pas un nombre",
            "il valore non è un numero",
            "значение не является числом",
        )

        ParameterRule3D.MUST_BE_POSITIVE -> t(
            "wartość musi być dodatnia",
            "the value must be positive",
            "der Wert muss positiv sein",
            "la valeur doit être positive",
            "il valore deve essere positivo",
            "значение должно быть положительным",
        )

        ParameterRule3D.BELOW_MINIMUM -> t(
            "poniżej minimum",
            "below the minimum",
            "unter dem Minimum",
            "en dessous du minimum",
            "sotto il minimo",
            "меньше минимума",
        )

        ParameterRule3D.ABOVE_MAXIMUM -> t(
            "powyżej maksimum",
            "above the maximum",
            "über dem Maximum",
            "au-dessus du maximum",
            "sopra il massimo",
            "больше максимума",
        )

        ParameterRule3D.OUT_OF_RANGE -> t(
            "poza dozwolonym zakresem",
            "outside the allowed range",
            "außerhalb des zulässigen Bereichs",
            "hors de la plage autorisée",
            "fuori dall'intervallo consentito",
            "вне допустимого диапазона",
        )

        ParameterRule3D.WALL_TOO_THICK -> t(
            "ścianka nie zostawia światła rury",
            "the wall leaves no bore",
            "die Wand lässt keine Bohrung",
            "la paroi ne laisse pas d'alésage",
            "la parete non lascia il foro",
            "стенка не оставляет проходного сечения",
        )

        ParameterRule3D.RADIUS_TOO_SMALL -> t(
            "promień jest mniejszy niż rura",
            "the radius is smaller than the tube",
            "der Radius ist kleiner als das Rohr",
            "le rayon est plus petit que le tube",
            "il raggio è minore del tubo",
            "радиус меньше трубы",
        )

        ParameterRule3D.DIAMETER_MISMATCH -> t(
            "średnice się nie zgadzają",
            "the diameters do not match",
            "die Durchmesser passen nicht",
            "les diamètres ne correspondent pas",
            "i diametri non corrispondono",
            "диаметры не совпадают",
        )

        ParameterRule3D.CONNECTION_MISMATCH -> t(
            "rodzaje połączeń się nie zgadzają",
            "the connection kinds do not match",
            "die Verbindungsarten passen nicht",
            "les types de raccord ne correspondent pas",
            "i tipi di giunzione non corrispondono",
            "типы соединения не совпадают",
        )

        ParameterRule3D.ANGLE_NOT_ALLOWED -> t(
            "kąt spoza dozwolonego zakresu",
            "the angle is outside the allowed window",
            "der Winkel liegt außerhalb des Fensters",
            "l'angle est hors de la fenêtre autorisée",
            "l'angolo è fuori dalla finestra consentita",
            "угол вне разрешённого окна",
        )

        ParameterRule3D.BLANK_ID -> t(
            "pusty identyfikator",
            "blank identifier",
            "leerer Bezeichner",
            "identifiant vide",
            "identificatore vuoto",
            "пустой идентификатор",
        )

        ParameterRule3D.DUPLICATE_ID -> t(
            "identyfikator już istnieje",
            "the identifier already exists",
            "der Bezeichner existiert bereits",
            "l'identifiant existe déjà",
            "l'identificatore esiste già",
            "идентификатор уже существует",
        )

        ParameterRule3D.EMPTY_SET -> t(
            "zbiór nie może być pusty",
            "the set cannot be empty",
            "die Menge darf nicht leer sein",
            "l'ensemble ne peut pas être vide",
            "l'insieme non può essere vuoto",
            "набор не может быть пустым",
        )
    }

    private fun quota(quota: EngineQuota3D): String = when (quota) {
        EngineQuota3D.PARTS -> t("Limit elementów", "Part limit", "Bauteilgrenze", "Limite de pièces", "Limite componenti", "Лимит деталей")
        EngineQuota3D.TRIANGLES -> t("Limit siatki", "Mesh limit", "Netzgrenze", "Limite du maillage", "Limite mesh", "Лимит сетки")
        EngineQuota3D.UNDO_DEPTH -> t("Limit historii", "History limit", "Verlaufsgrenze", "Limite d'historique", "Limite cronologia", "Лимит истории")
        EngineQuota3D.ELBOWS -> t("Limit kolan", "Elbow limit", "Bogengrenze", "Limite de coudes", "Limite curve", "Лимит отводов")
        EngineQuota3D.ROUTE_CANDIDATES -> t("Limit wariantów", "Candidate limit", "Variantengrenze", "Limite de variantes", "Limite varianti", "Лимит вариантов")
        EngineQuota3D.SOLVER_ITERATIONS -> t("Limit iteracji", "Iteration limit", "Iterationsgrenze", "Limite d'itérations", "Limite iterazioni", "Лимит итераций")
    }

    private fun route(code: RouteFailureCode3D): String = when (code) {
        RouteFailureCode3D.INVALID_REQUEST -> t(
            "Niepoprawne dane trasy",
            "The route request is invalid",
            "Ungültige Routenanfrage",
            "Demande de tracé invalide",
            "Richiesta di percorso non valida",
            "Некорректные данные трассы",
        )

        RouteFailureCode3D.TARGET_BEHIND_START -> t(
            "Cel leży za punktem startu",
            "The target is behind the start face",
            "Das Ziel liegt hinter dem Startflansch",
            "La cible est derrière la face de départ",
            "Il bersaglio è dietro la faccia iniziale",
            "Цель находится позади начального торца",
        )

        RouteFailureCode3D.MAX_ELBOWS_TOO_SMALL -> t(
            "Za mało dozwolonych kolan",
            "Too few elbows are allowed",
            "Zu wenige Bögen erlaubt",
            "Trop peu de coudes autorisés",
            "Troppo poche curve consentite",
            "Разрешено слишком мало отводов",
        )

        RouteFailureCode3D.NO_FABRICABLE_ROUTE -> t(
            "Brak wykonalnej trasy dla tych parametrów",
            "No fabricable route fits these parameters",
            "Keine fertigbare Route für diese Parameter",
            "Aucun tracé réalisable pour ces paramètres",
            "Nessun percorso realizzabile con questi parametri",
            "Нет выполнимой трассы при этих параметрах",
        )

        RouteFailureCode3D.OBSTRUCTED -> t(
            "Trasa przechodzi przez przeszkodę",
            "The route runs through an obstacle",
            "Die Route trifft ein Hindernis",
            "Le tracé traverse un obstacle",
            "Il percorso attraversa un ostacolo",
            "Трасса проходит через препятствие",
        )

        RouteFailureCode3D.INTERNAL_CLOSURE_ERROR -> t(
            "Trasa nie domknęła się w tolerancji",
            "The route did not close within tolerance",
            "Die Route schloss nicht in der Toleranz",
            "Le tracé ne s'est pas fermé dans la tolérance",
            "Il percorso non si è chiuso entro la tolleranza",
            "Трасса не замкнулась в пределах допуска",
        )
    }

    private fun action(action: EditorAction3D): String = when (action) {
        EditorAction3D.UNDO -> t("Nie ma czego cofnąć", "Nothing to undo", "Nichts rückgängig zu machen", "Rien à annuler", "Niente da annullare", "Отменять нечего")
        EditorAction3D.REDO -> t("Nie ma czego ponowić", "Nothing to redo", "Nichts zu wiederholen", "Rien à rétablir", "Niente da ripetere", "Повторять нечего")
        else -> t("Operacja niedostępna", "The operation is unavailable", "Vorgang nicht verfügbar", "Opération indisponible", "Operazione non disponibile", "Операция недоступна")
    }
}
