(ns frontpage-re-frame.views.editable 
  (:require [re-frame.core :as re-frame]
            [frontpage-re-frame.views.utils :refer [<sub >evt]]))

(defn update-document-field-handler [field]
  (fn [e]
    (.preventDefault e)
    (>evt [:update-document-field field (.. e -target -value)])))

(defn editable-document [doc]
  [:form.document.inline {:on-submit (fn [e]
                                       (.preventDefault e)
                                       (>evt [:update-document]))}
   [:div.field
    [:label "Title:"
     [:input.input {:type "text"
                    :name "title"
                    :default-value (:title doc)
                    :on-input (update-document-field-handler :title)}]]]
   [:div.field
    [:label "Body:"
     [:textarea.textarea {:name "body"
                          :rows 20
                          :defaultValue (:body doc)
                          :on-input (update-document-field-handler :body)}]]]
   [:div.field.is-grouped
    [:div.control
     [:input.button.is-primary {:type "submit" :value "Submit"}]]
    [:div.control
     [:a.button.is-light {:href "#" :on-click (fn [e]
                                                (.preventDefault e)
                                                (>evt [:unedit-document-result]))}
      "cancel"]]]])
