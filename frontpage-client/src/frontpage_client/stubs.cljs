(ns frontpage-client.stubs)

;; React requires a global console variable.
(set! js/console #js {:log js/print :warn js/print :error js/print})

;; Jayq wants jQuery.
(set! js/jQuery (fn []))
(set! (.. js/jQuery -ajaxSetup) (fn [x]))

;; Stubs for the i18n library
(set! js/i18n (fn []))
(set! (.. js/i18n -init) (fn []))
(set! (.. js/i18n -t) (fn [s d] s))

;; Needed by core.async
(set! (.. js/goog.global -setTimeout) (fn [cb t] (cb)))
