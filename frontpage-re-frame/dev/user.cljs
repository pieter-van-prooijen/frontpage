(ns user)

(defn pp [x]
  "Pretty print x to a string"
  (with-out-str (cljs.pprint/pprint x)))

