(ns convert.netcdf
  (:gen-class)
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as cs]
            [clj-time.core :as ctc]
            [clojure-csv.core :as csv]
            [clj-time.coerce :as ctcoe]
            [clj-time.format :as ctf]
            [netcdf.dataset :as nd]))

(def opened-datasets (atom {}))
(def variables (atom {}))

(defn open-datasets [path from-year to-year]
  (doseq [[filename-start sym var-name unit]
          [["ASWDIFD_S_ts_" :aswdifd_s "ASWDIFD_S" :W_m-2]
           ["ASWDIR_S_ts_" :aswdir_s "ASWDIR_S" :W_m-2]
           ["DURSUN_ts_" :dursun "DURSUN" :s]
           ["RELHUM_2M_ts_" :relhum_2m "RELHUM_2M" :%]
           ["T_2M_ts_" :t_2m "T_2M" :K]
           #_["TD_2M_ts_" :td_2m "TD_2M" :K]                      ; dew point temp = Taupunkt
           ["TOT_PREC_ts_" :tot_prec "TOT_PREC" :kg_m-2]           ; total precipition
           ["U_10M_ts_" :u_10m "U_10M" :m_s-1]                  ; wind u component 10m
           ["V_10M_ts_" :v_10m "V_10M" :m_s-1]                  ; wind v component 10m
           ]
          year (range from-year (inc to-year))
          :let [dataset (nd/open-dataset (str path "/" filename-start year ".nc"))]]
    (swap! opened-datasets assoc-in [year sym] dataset)
    (swap! variables assoc-in [year sym] (.findVariable dataset var-name))
    (println "opened dataset: " (str path "/" filename-start year ".nc")))
  (println "opened all datasets from year " from-year " to year " to-year))

(defn close-datasets []
  (doseq [[_year syms] @opened-datasets
          [_ dataset] syms]
    (.close dataset)))

(def csv-header
  ["day" "month" "year" "date" "tmin" "tavg" "tmax" "precip" "globrad" "relhumid" "windspeed"])

(defn round [value & {:keys [digits] :or {digits 0}}]
  (let [factor (Math/pow 10 digits)]
    (-> value
        (* factor)
        Math/round
        (/ factor))))

(defn write-climate-files* [path from-year to-year from-lat to-lat from-lon to-lon #_skip-header? append-to-file?]
  (doseq [lat-i (range from-lat to-lat)
          lon-i (range from-lon to-lon)]
    (let [path-to-file (str path "/row-" lat-i "/col-" lon-i ".txt")
          _ (io/make-parents path-to-file)
          _ (println "writing file: " path-to-file)]
      (with-open [w (if append-to-file?
                      (clojure.java.io/writer path-to-file :append true)
                      (clojure.java.io/writer path-to-file))]
        (when-not append-to-file? #_skip-header?
          (.write w (csv/write-csv [csv-header])))

        (doseq [year (range from-year (inc to-year))]
          (let [leap-year? (.. (ctc/date-time year) year isLeap)
                days-in-year (if leap-year? 366 365)
                sym-to-var (get @variables year)

                  to-part-seq-3 (fn [arr]
                                  (partition 24 (for [i (range 0 (.getSize arr))]
                                                  (.get arr i 0 0))))

                  to-part-seq-4 (fn [arr]
                                  (partition 24 (for [i (range 0 (.getSize arr))]
                                                  (.get arr i 0 0 0))))

                  sum #(reduce + 0 %)
                  avg #(/ (sum %) 24)

                ;#_(
                    ;get all data for a single year (hourly values)
                  [tmins* tavgs* tmaxs*] (-> sym-to-var
                                             :t_2m
                                             (.read,,, (str ":,0," lat-i "," lon-i))
                                             to-part-seq-4
                                             ((juxt (partial map (partial apply min))
                                                    (partial map avg)
                                                    (partial map (partial apply max))
                                                    ) ,,,))
                  tmins (map #(round (- % 273.15) :digits 1) tmins*)
                  tavgs (map #(round (- % 273.15) :digits 1) tavgs*)
                  tmaxs (map #(round (- % 273.15) :digits 1) tmaxs*)
                  ;_ (println "tavgs: " tavgs)
                  ;)

                #_([tmins tavgs tmaxs] (let [arr (-> sym-to-var
                                                  :t_2m
                                                  (.read,,, (str ":,0," lat-i "," lon-i)))
                                          size (.getSize arr)]
                                      (loop [tmins []
                                             tavgs []
                                             tmaxs []
                                             i 0]
                                        (if (< i size)
                                          (let [[tmin tavg tmax] (loop [tmin 99999
                                                                        tsum 0
                                                                        tmax -9999
                                                                        i24 i]
                                                                   (let [t (- (.get arr i24 0 0 0) 273.15)
                                                                         tmin* (min tmin t)
                                                                         tsum* (+ tsum t)
                                                                         tmax* (max t tmax)]
                                                                     (if (= (mod (inc i24) 24) 0)
                                                                       [(round tmin* :digits 1)
                                                                        (round (/ tsum* 24) :digits 1)
                                                                        (round tmax* :digits 1)]
                                                                       (recur tmin* tsum* tmax* (inc i24)))))]
                                            (recur (conj tmins tmin)
                                                   (conj tavgs tavg)
                                                   (conj tmaxs tmax)
                                                   (+ i 24)))
                                          [tmins tavgs tmaxs])))
                ;_ (println "tavgs: " tavgs #_(interleave tavgs tavgs**))
)
                ;#_(
                  dir-rad (-> sym-to-var
                              :aswdir_s
                              (.read,,, (str ":," lat-i "," lon-i))
                              to-part-seq-3
                              (#(map sum %),,,)
                              doall)
                  dif-rad (-> sym-to-var
                              :aswdifd_s
                              (.read,,, (str ":," lat-i "," lon-i))
                              to-part-seq-3
                              (#(map sum %),,,)
                              doall)
                  globrads* (map + dir-rad dif-rad)
                  ;convert Wh*m-2 to MJ*m-2 via 1Wh = 3600J and 1MJ = 1.000.000J
                  ;the Netcdf just tells about W*m-2 as Unit, but this is the
                  ;radiation collected within 1 hour
                  globrads (->> globrads*
                                  (map #(round (* 3.6 0.001 %) :digits 2) ,,,))
                  ;_ (println "globrads: " globrads)
                  ;)

                #_(globrads (let [dir-arr (-> sym-to-var
                                          :aswdir_s
                                          (.read,,, (str ":," lat-i "," lon-i)))
                              dif-arr (-> sym-to-var
                                          :aswdifd_s
                                          (.read,,, (str ":," lat-i "," lon-i)))
                              size (.getSize dir-arr)]
                          (loop [globrads []
                                 i 0]
                            (if (< i size)
                              (let [globrad (loop [globrad-sum 0
                                                   i24 i]
                                              (let [dir (.get dir-arr i24 0 0)
                                                    dif (.get dif-arr i24 0 0)
                                                    globrad-sum* (+ globrad-sum dir dif)]
                                                (if (= (mod (inc i24) 24) 0)
                                                  (round (* 3.6 0.001 globrad-sum*) :digits 2)
                                                  (recur globrad-sum* (inc i24)))))]
                                (recur (conj globrads globrad)
                                       (+ i 24)))
                              globrads)))
                ;_ (println "globrads: " globrads #_(interleave globrads globrads**))
)
                #_sunhours #_(-> sym-to-var
                             :dursun
                             (.read,,, (str ":," lat-i "," lon-i))
                             to-part-seq-3
                             (#(map (fn [vs] (/ (sum vs) 3600.)) %),,,))

                ;#_(
                  relhums (-> sym-to-var
                               :relhum_2m
                               (.read,,, (str ":,0," lat-i "," lon-i))
                               to-part-seq-4
                               (#(map (fn [rhs] (round (avg rhs) :digits 1)) %),,,))
                  ;)

                #_(relhums (let [arr (-> sym-to-var
                                          :relhum_2m
                                          (.read,,, (str ":,0," lat-i "," lon-i)))
                              size (.getSize arr)]
                           (loop [relhums []
                                  i 0]
                             (if (< i size)
                               (let [avg-relhum (loop [relhum-sum 0
                                                       i24 i]
                                                  (let [relhum (.get arr i24 0 0 0)
                                                        relhum-sum* (+ relhum-sum relhum)]
                                                    (if (= (mod (inc i24) 24) 0)
                                                      (round (/ relhum-sum* 24) :digits 1)
                                                      (recur relhum-sum* (inc i24)))))]
                                 (recur (conj relhums avg-relhum)
                                        (+ i 24)))
                               relhums)))
                ;_ (println "relhums: " relhums #_(interleave relhums relhums*))
)
                ;#_(
                  precips (-> sym-to-var
                               :tot_prec
                               (.read,,, (str ":," lat-i "," lon-i))
                               to-part-seq-3
                               (#(map (fn [ps] (round (sum ps) :digits 1)) %),,,))
                  ;_ (println "precips: " precips*)
                ;)

                #_(precips (let [arr (-> sym-to-var
                                      :tot_prec
                                      (.read,,, (str ":," lat-i "," lon-i)))
                              size (.getSize arr)]
                          (loop [precips []
                                 i 0]
                            (if (< i size)
                              (let [precip-sum (loop [precip-sum 0
                                                      i24 i]
                                                 (let [precip (.get arr i24 0 0)
                                                       precip-sum* (+ precip-sum precip)
                                                       ]
                                                   (if (= (mod (inc i24) 24) 0)
                                                     (round precip-sum* :digits 1)
                                                     (recur precip-sum* (inc i24)))))]
                                (recur (conj precips precip-sum)
                                       (+ i 24)))
                              precips)))
                ;_ (println "precips: " precips #_(interleave precips precips*))
)
                ;#_(
                u-wind-arr (-> sym-to-var :u_10m (.read ,,, (str ":,0," lat-i "," lon-i)))
                v-wind-arr (-> sym-to-var :v_10m (.read ,,, (str ":,0," lat-i "," lon-i)))
                winds (->> (for [i (range 0 (.getSize u-wind-arr))]
                             [(.get u-wind-arr i 0 0 0)
                              (.get v-wind-arr i 0 0 0)])
                           (partition 24,,,)
                           (map (fn [uvs] (round (/ (reduce (fn [acc [u v]]
                                                              (+ acc (Math/sqrt (+ (* u u) (* v v)))))
                                                            0 uvs)
                                                    24)
                                                 :digits 1)) ,,,))
                ;_ (println "winds: " winds)
                ;)

                #_(winds (let [u-wind-arr (-> sym-to-var
                                           :u_10m
                                           (.read,,, (str ":,0," lat-i "," lon-i)))
                            v-wind-arr (-> sym-to-var
                                           :v_10m
                                           (.read,,, (str ":,0," lat-i "," lon-i)))
                            size (.getSize u-wind-arr)]
                           (loop [winds []
                                  i 0]
                             (if (< i size)
                               (let [wind (loop [wind-sum 0
                                                 i24 i]
                                            (let [u (.get u-wind-arr i24 0 0 0)
                                                  v (.get v-wind-arr i24 0 0 0)
                                                  wind-sum* (+ wind-sum (Math/sqrt (+ (* u u) (* v v))))]
                                              (if (= (mod (inc i24) 24) 0)
                                                (round (/ wind-sum* 24) :digits 1)
                                                (recur wind-sum* (inc i24)))))]
                                 (recur (conj winds wind)
                                        (+ i 24)))
                               winds)))
                ;_ (println "winds: " (interleave winds winds*))
)

                ;#_(
                [days
                 months
                 years
                 dates] ((juxt (partial map first)
                                (partial map second)
                                (partial map #(nth % 2))
                                (partial map #(nth % 3)))
                                           (doall (for [diy (range 1 (inc days-in-year))]
                                                    (let [date (ctc/plus (ctc/date-time year) (ctc/days (dec diy)))]
                                                      [(ctc/day date)
                                                       (ctc/month date)
                                                       year
                                                       (ctf/unparse (ctf/formatter "yyyy-MM-dd") date)]))))

                ;_ (println "days: " days)
                ;_ (println "dates: " dates)
                ;    )

                #_([days months years dates] (loop [days []
                                                 months []
                                                 years []
                                                 dates []
                                                 diy 1]
                                            (if (< diy (inc days-in-year))
                                              (let [date (ctc/plus (ctc/date-time year) (ctc/days (dec diy)))]
                                                (recur (conj days (ctc/day date))
                                                       (conj months (ctc/month date))
                                                       (conj years year)
                                                       (conj dates (ctf/unparse (ctf/formatter "yyyy-MM-dd") date))
                                                       (inc diy)))
                                              [days months years dates]))

                ;_ (println "days: " days #_(interleave days days*))
                ;_ (println "dates: " dates #_(interleave dates dates*))
)
                row-strs (map (fn [& vs] (map str vs))
                              days months years dates tmins tavgs tmaxs precips globrads relhums winds)]
            (doseq [row-str row-strs]
              #_(println "row-str: " row-str)
              (.write w (csv/write-csv [row-str])))))))))

(defn run-climate-file-conversion [& {:strs [read-path write-path from-year to-year
                                             from-lat to-lat from-lon to-lon
                                             #_skip-header? append-to-file?]}]
  (let [read-path (or read-path "in-data")
        write-path (or write-path "out-data")
        from-year (or (some-> from-year edn/read-string) 1994)
        to-year (or (some-> to-year edn/read-string) 2012)
        from-lat (or (some-> from-lat edn/read-string) 0)
        to-lat (or (some-> to-lat edn/read-string) 441)
        from-lon (or (some-> from-lon edn/read-string) 0)
        to-lon (or (some-> to-lon edn/read-string) 400)
        ;skip-header? (or (some-> skip-header? edn/read-string) false)
        append-to-file? (or (some-> append-to-file? edn/read-string) false)]
    (try (open-datasets read-path from-year to-year)
         (write-climate-files* write-path from-year to-year
                               from-lat to-lat from-lon to-lon
                               #_skip-header? append-to-file?)
         (finally (close-datasets)))))

(defn -main
  [& kvs]
  (apply run-climate-file-conversion kvs))

#_(-main "read-path" "Z:\\DWD"
       "write-path" "Z:\\DWD-out"
       "from-year" "1994"
       "to-year" "2012")

;java -jar target\uberjar\convert-climate-data-1.0-standalone.jar read-path z:/DWD write-path d:/DWD-out from-year 1994 to-year 1996










