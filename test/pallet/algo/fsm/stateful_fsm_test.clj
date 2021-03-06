(ns pallet.algo.fsm.stateful-fsm-test
  (:use
   clojure.test
   pallet.algo.fsm.stateful-fsm
   [pallet.algo.fsm.fsm :only [with-transition-observer]]))

(deftest fsm-test
  (testing "no features"
    (let [{:keys [transition state reset valid-state? valid-transition?] :as sm}
          (stateful-fsm {:state-kw :locked :state-data {:code "123"}}
                        {:locked #{:open :locked} :open #{:locked}}
                        nil
                        nil)
          fsm (:fsm (state))]

      (is (valid-state? {:state-kw :locked}) "recognises valid states")
      (is (valid-state? {:state-kw :open}) "recognises valid states")
      (is (not (valid-state? :broken)) "recognises invalid states")

      (is (valid-transition? {:state-kw :open})
          "recognises valid transitions")
      (is (valid-transition? {:state-kw :locked})
          "recognises valid transitions")
      (is (not (valid-transition? :broken)) "recognises invalid transitions")

      (is (= {:state-kw :locked
              :state-data {:code "123"}}
             (state)))
      (is (= {:state-kw :locked
              :state-data {:so-far "1"}}
             (transition
              #(assoc % :state-kw :locked :state-data {:so-far "1"}))))
      (is (= {:state-kw :open
              :state-data {:so-far "2"}}
             (transition
              #(assoc % :state-kw :open :state-data {:so-far "2"}))))))

  (testing "timeout"
    (let [{:keys [transition state reset] :as sm}
          (stateful-fsm {:state-kw :locked :state-data {:code "123"}}
                        {:locked #{:open :timed-out} :open #{:locked}}
                        nil
                        [:timeout])]
      (is (= {:state-kw :locked
              :state-data {:code "123"}}
             (state)))
      (is (= {:state-kw :open
              :state-data {:so-far "1"}}
             (transition
              #(assoc % :state-kw :open :state-data {:so-far "1"}))))
      (testing "timeout"
        (is (= {:state-kw :locked
                :state-data {:so-far "1"}}
               (transition #(assoc % :state-kw :locked :timeout {:s 1}))))
        (Thread/sleep 2000)
        (is (= {:state-kw :timed-out
                :state-data {:so-far "1"}}
               (state))))))

  (testing "on-entry on-exit"
    (let [exit-locked (atom nil)
          enter-open (atom nil)
          {:keys [transition state reset] :as sm}
          (stateful-fsm {:state-kw :locked :state-data {:code "1"}}
                        {:locked {:transitions #{:locked :open}
                                  :on-exit (fn [_] (reset! exit-locked true))}
                         :open {:transitions #{:locked}
                                :on-enter (fn [_] (reset! enter-open true))}}
                        [:on-enter-exit]
                        nil)]
      (is (= {:state-kw :locked :state-data {:code "1"}}
             (state)))
      (is (= {:state-kw :open
              :state-data {:so-far nil}}
             (transition
              #(assoc % :state-kw :open :state-data {:so-far nil}))))
      (is @exit-locked)
      (is @enter-open)))

  (testing "history"
    (let [{:keys [transition state reset] :as sm}
          (stateful-fsm {:state-kw :locked :state-data {:code "1"}}
                        {:locked {:transitions #{:locked :open}}
                         :open {:transitions #{:locked}}}
                        nil
                        [:history])]
      (is (= {:state-kw :locked :state-data {:code "1"}}
             (state)))
      (is (= {:state-kw :open
              :state-data {:so-far nil}
              :history [{:state-kw :locked :state-data {:code "1"}}]}
             (transition
              #(assoc % :state-kw :open :state-data {:so-far nil}))))))

  (testing "observer"
    (let [a (atom [])
          {:keys [transition state reset] :as sm}
          (stateful-fsm {:state-kw :locked :state-data {:code "1"}}
                        {:locked {:transitions #{:locked :open}}
                         :open {:transitions #{:locked}}}
                        [(with-transition-observer
                           (fn [old new] (swap! a concat [old new])))]
                        nil)]
      (is (= {:state-kw :locked :state-data {:code "1"}}
             (state)))
      (is (= {:state-kw :open
              :state-data {:so-far nil}}
             (transition
              #(assoc % :state-kw :open :state-data {:so-far nil}))))
      (is (= [{:state-kw :locked, :state-data {:code "1"}}
              {:state-kw :open, :state-data {:so-far nil}}]
             @a)))))
