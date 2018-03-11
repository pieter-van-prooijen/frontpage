(ns frontpage-re-frame.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            
            ;; explicit requires for the Closure compiler, won't be executed otherwise
            [frontpage-re-frame.handlers.core]
            [frontpage-re-frame.handlers.editable]
            [frontpage-re-frame.subs]
            [frontpage-re-frame.routes :as routes]
            [frontpage-re-frame.views.core :as views]
            [frontpage-re-frame.config :as config]))

(when config/debug?
  (println "dev mode"))

(defn mount-root []
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init [] 
  (routes/app-routes)
  (re-frame/dispatch-sync [:initialize-db])
  (mount-root))
