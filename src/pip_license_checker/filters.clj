;; Copyright © 2020, 2021 Vitaly Samigullin
;;
;; This program and the accompanying materials are made available under the
;; terms of the Eclipse Public License 2.0 which is available at
;; http://www.eclipse.org/legal/epl-2.0.
;;
;; This Source Code may also be made available under the following Secondary
;; Licenses when the conditions for such availability set forth in the Eclipse
;; Public License, v. 2.0 are satisfied: GNU General Public License as published by
;; the Free Software Foundation, either version 2 of the License, or (at your
;; option) any later version, with the GNU Classpath Exception which is available
;; at https://www.gnu.org/software/classpath/license.html.
;;
;; SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0

(ns pip-license-checker.filters
  "Filters for requirements"
  (:gen-class)
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   ;;[clojure.spec.test.alpha :refer [instrument]]
   [pip-license-checker.spec :as sp]
   [pip-license-checker.version :as version]))

;; Skip line with -r /--requirement/-e etc, URLs, blank lines, comments
(def regex-skip-line-internal #"(\s*(?:https?:\/\/|#|-).*)|(^\s*$)")

(def regex-remove-whitespace #"\s*")
(def regex-remove-comment #"#.*")
(def regex-remove-modifiers #"(;|@).*")
(def regex-remove-extra #"\[.*\]")
(def regex-remove-wildcard #"\.\*")
(def regex-split-specifier-ops #"(===|==|~=|!=|>=|<=|<|>)")

(s/fdef remove-requirements-internal-rules
  :args (s/cat :requirements ::sp/requirements)
  :ret ::sp/requirements)

(defn remove-requirements-internal-rules
  "Exclude requirements from sequence according to app's internal rules"
  [requirements]
  (remove #(re-matches regex-skip-line-internal %) requirements))

(defn- requirement-matching?
  "Match requirement against regex, catch NPE in case of null values"
  [requirement pattern]
  (try
    (re-matches pattern requirement)
    (catch NullPointerException _ false)))

(s/fdef remove-requirements-user-rules
  :args (s/cat :pattern ::sp/opt-pattern :requirements ::sp/requirements)
  :ret ::sp/requirements)

(defn remove-requirements-user-rules
  "Exclude requirement strings from sequence according to user-defined pattern.
  Used for requirements pre-processing"
  [pattern requirements]
  (if pattern
    (remove #(requirement-matching? % pattern) requirements)
    requirements))

(defn- requirement-name-matching?
  "Match requirement name against regex, catch NPE in case of null values"
  [requirement pattern]
  (try
    (re-matches pattern (get-in requirement [:requirement :name]))
    (catch NullPointerException _ false)))

(defn remove-requiment-maps-user-rules
  "Exclude requiement objects to user-defined pattern
  Used for requirements post-processing"
  [pattern packages]
  (if pattern
    (remove #(requirement-name-matching? % pattern) packages)
    packages))

(defn sanitize-requirement
  "Sanitize requirement line"
  [requirement]
  (->
   requirement
   (str/replace regex-remove-whitespace "")
   (str/replace regex-remove-comment "")
   (str/replace regex-remove-modifiers "")
   (str/replace regex-remove-extra "")
   (str/replace regex-remove-wildcard "")))

(s/fdef requirement->map
  :args (s/cat :requirement ::sp/requirement)
  :ret ::sp/requirement-map)

(defn requirement->map
  "Parse requirement string into map with package name and its specifiers parsed"
  [requirement]
  (let [package-name (first (str/split requirement regex-split-specifier-ops))
        specifiers-str (subs requirement (count package-name))
        specifiers-vec (version/parse-specifiers specifiers-str)
        specifiers (if (= specifiers-vec [nil]) nil specifiers-vec)
        result {:name package-name :specifiers specifiers}]
    result))

(defn filter-fails-only-licenses
  "Filter license types specified with --failed flag(s) if needed"
  [options licenses]
  (let [{:keys [fail fails-only]} options]
    (if (or
         (not fails-only)
         (not (seq fail)))
      licenses
      (filter #(contains? fail (get-in % [:license :type])) licenses))))

(defn- license-name-matching?
  "Match license name against regex, catch NPE in case of null values"
  [license pattern]
  (try
    (re-matches pattern (get-in license [:license :name]))
    (catch NullPointerException _ false)))

(defn remove-licenses
  "Remove parsed licenses matching the pattern"
  [options licenses]
  (let [{:keys [exclude-license]} options]
    (if exclude-license
      (remove #(license-name-matching? % exclude-license) licenses)
      licenses)))

(defn filter-parsed-requirements
  "Post parsing filtering pipeline"
  [licenses options]
  (->> (filter-fails-only-licenses options licenses)
       (remove-licenses options)))

;;
;; Instrumented functions - uncomment only while testing
;;

;; (instrument `remove-requirements-internal-rules)
;; (instrument `remove-requirements-user-rules)
;; (instrument `requirement->map)
