(ns bookmarx.util)

(defn make-dict []
  (let [vals (range 256)]
    (zipmap (map vector vals) vals)))


(defn compress- [{:keys [dict index w out] :as a} b]
  (let [buffer (conj w b)]
    (if (contains? dict buffer)
      (assoc a :w buffer)
      {:dict (assoc dict buffer index)
       :index (inc index)
       :out (conj out (get dict w))
       :w [b]})))


(defn compress [data]
  (let [initial-data {:dict (make-dict) :index 256 :w [] :out []}
        {:keys [dict index w out] :as a} (reduce compress- initial-data (seq data))]
    (conj out (get dict w))))


(defn decompress- [{:keys [dict index w out] :as a} code]
  (let [entry (if (contains? dict code) (get dict code) (conj w (first w)))]
    {:dict (assoc dict index (conj w (first entry)))
     :index (inc index)
     :out (conj out entry)
     :w entry}))


(defn decompress [data]
  (let [f [(first data)]
        initial-data {:dict (clojure.set/map-invert (make-dict)) :index 256 :w f :out f}
        result (reduce decompress- initial-data (rest data))]
    (flatten (:out result))))
