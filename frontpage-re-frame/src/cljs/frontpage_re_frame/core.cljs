(ns frontpage-re-frame.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [frontpage-re-frame.handlers] ; explicit require for Closure compiler
              [frontpage-re-frame.subs]   ; same here
              [frontpage-re-frame.routes :as routes]
              [frontpage-re-frame.views :as views]
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
