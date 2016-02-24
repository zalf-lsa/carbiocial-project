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
           ["TD_2M_ts_" :td_2m "TD_2M" :K]                      ; dew point temp = Taupunkt
           ["TOT_PREC_ts_" :tot_prec "TOT_PREC" :kg_m-2]           ; total precipition
           ["U_10M_ts_" :u_10m "U_10M" :m_s-1]                  ; wind u component 10m
           ["V_10M_ts_" :v_10m "V_10M" :m_s-1]                  ; wind v component 10m
           ]
          year (range from-year (inc to-year))
          :let [dataset (nd/open-dataset (str path "/" filename-start year ".nc"))]]
    (swap! opened-datasets assoc-in [year sym] dataset)
    (swap! variables assoc-in [year sym] (.findVariable dataset var-name))
    #_(println "opened dataset: " (str path "/" filename-start year ".nc")))
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

                dir-rad (-> sym-to-var
                            :aswdir_s
                            (.read,,, (str ":," lat-i "," lon-i))
                            to-part-seq-3
                            (#(map sum %),,,))
                dif-rad (-> sym-to-var
                            :aswdifd_s
                            (.read,,, (str ":," lat-i "," lon-i))
                            to-part-seq-3
                            (#(map sum %),,,))
                globrads* (map + dir-rad dif-rad)
                ;convert Wh*m-2 to MJ*m-2 via 1Wh = 3600J and 1MJ = 1.000.000J
                ;the Netcdf just tells about W*m-2 as Unit, but this is the
                ;radiation collected within 1 hour
                globrads (map #(round (* 3.6 0.001 %) :digits 2) globrads*)
                ;_ (println "globrads: " globrads)

                #_sunhours #_(-> sym-to-var
                             :dursun
                             (.read,,, (str ":," lat-i "," lon-i))
                             to-part-seq-3
                             (#(map (fn [vs] (/ (sum vs) 3600.)) %),,,))

                relhums (-> sym-to-var
                           :relhum_2m
                           (.read,,, (str ":,0," lat-i "," lon-i))
                           to-part-seq-4
                           (#(map (fn [rhs] (round (avg rhs) :digits 1)) %),,,))

                precips (-> sym-to-var
                           :tot_prec
                           (.read,,, (str ":," lat-i "," lon-i))
                           to-part-seq-3
                           (#(map (fn [ps] (round (sum ps) :digits 1)) %),,,))
                ;_ (println "precips: " precips)

                u-wind-arr (-> sym-to-var :u_10m (.read,,, (str ":,0," lat-i "," lon-i)))
                v-wind-arr (-> sym-to-var :v_10m (.read,,, (str ":,0," lat-i "," lon-i)))
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

                [days months years dates] ((juxt (partial map first)
                                                 (partial map second)
                                                 (partial map #(nth % 2))
                                                 (partial map #(nth % 3)))
                                            (for [diy (range 1 (inc days-in-year))]
                                              (let [date (ctc/plus (ctc/date-time year) (ctc/days (dec diy)))]
                                                [(ctc/day date)
                                                 (ctc/month date)
                                                 year
                                                 (ctf/unparse (ctf/formatter "yyyy-MM-dd") date)])))

                ;_ (println "days: " days)
                ;_ (println "dates: " dates)

                row-strs (map (fn [& vs] (map str vs))
                              days months years dates tmins tavgs tmaxs precips globrads relhums winds)
                ]
            (doseq [row-str row-strs]
              ;(println "row-str: " row-str)
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












