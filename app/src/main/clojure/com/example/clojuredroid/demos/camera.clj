(ns com.example.clojuredroid.demos.camera
  "Camera demo: CameraX preview with photo capture."
  (:require [neko.activity :as act]
            [neko.reactive :refer [cell]]
            [neko.resource :refer [get-theme-color]]
            [neko.threading :refer [on-ui]])
  (:import android.Manifest$permission
           android.content.pm.PackageManager
           android.hardware.camera2.CameraCharacteristics
           android.view.View
           android.view.ViewGroup$LayoutParams
           android.widget.FrameLayout
           android.widget.FrameLayout$LayoutParams
           android.widget.LinearLayout$LayoutParams
           [androidx.camera.core
            AspectRatio
            CameraSelector
            ImageCapture ImageCapture$Builder
            ImageCapture$OnImageSavedCallback
            ImageCapture$OutputFileOptions$Builder
            Preview Preview$Builder
            UseCase]
           [androidx.camera.core.resolutionselector
            AspectRatioStrategy ResolutionSelector ResolutionSelector$Builder]
           androidx.camera.camera2.interop.Camera2CameraInfo
           androidx.camera.lifecycle.ProcessCameraProvider
           androidx.camera.view.PreviewView
           androidx.core.content.ContextCompat
           [androidx.lifecycle Lifecycle$Event LifecycleOwner LifecycleRegistry]
           android.content.ContentValues
           android.provider.MediaStore
           android.provider.MediaStore$Images$Media
           com.goodanser.clj_android.runtime.ClojureActivity))

;; ---------------------------------------------------------------------------
;; State
;; ---------------------------------------------------------------------------

(def status* (cell "Camera not started."))
(defonce ^:private camera-provider* (atom nil))
(defonce ^:private image-capture* (atom nil))
(defonce ^:private preview-view* (atom nil))
(defonce ^:private preview-container* (atom nil))

;; ---------------------------------------------------------------------------
;; LifecycleOwner for plain Activity
;; ---------------------------------------------------------------------------
;; ClojureActivity extends Activity (not AppCompatActivity), so it doesn't
;; implement LifecycleOwner. We create a standalone one and drive it manually.

(defonce ^:private lifecycle-owner* (atom nil))
(defonce ^:private lifecycle-registry* (atom nil))

(defn- ensure-lifecycle-owner! []
  (when-not @lifecycle-owner*
    (let [registry-ref (atom nil)
          owner (proxy [Object LifecycleOwner] []
                  (getLifecycle [] @registry-ref))
          registry (LifecycleRegistry. owner)]
      (reset! registry-ref registry)
      (reset! lifecycle-registry* registry)
      (reset! lifecycle-owner* owner)
      (.handleLifecycleEvent registry Lifecycle$Event/ON_CREATE)
      (.handleLifecycleEvent registry Lifecycle$Event/ON_START)
      (.handleLifecycleEvent registry Lifecycle$Event/ON_RESUME))))

;; ---------------------------------------------------------------------------
;; Sensor aspect ratio
;; ---------------------------------------------------------------------------

(def ^:private fallback-ratio 4/3)

(defn- sensor-aspect-ratio
  "Returns the native sensor width/height ratio for the back camera,
  or `fallback-ratio` if it cannot be determined."
  [^ProcessCameraProvider provider]
  (let [infos (.getAvailableCameraInfos provider)
        back  (first (filter
                       (fn [^androidx.camera.core.CameraInfo info]
                         (= (.getLensFacing info)
                            CameraSelector/LENS_FACING_BACK))
                       infos))]
    (if back
      (let [c2     (Camera2CameraInfo/from back)
            ^android.graphics.Rect active
            (.getCameraCharacteristic c2
              CameraCharacteristics/SENSOR_INFO_ACTIVE_ARRAY_SIZE)]
        (if active
          (/ (.width active) (.height active))
          fallback-ratio))
      fallback-ratio)))

(defn- closest-camerax-ratio
  "Maps a numeric ratio to the nearest CameraX AspectRatio constant."
  [ratio]
  (let [diff-4-3  (Math/abs (- (double ratio) 4/3))
        diff-16-9 (Math/abs (- (double ratio) 16/9))]
    (if (<= diff-4-3 diff-16-9)
      AspectRatio/RATIO_4_3
      AspectRatio/RATIO_16_9)))

(defn- portrait-rotation?
  "True when the display rotation is portrait (0° or 180°)."
  [rotation]
  (or (= rotation android.view.Surface/ROTATION_0)
      (= rotation android.view.Surface/ROTATION_180)))

(defn- resize-preview-container!
  "Adjusts the preview FrameLayout height to match the sensor aspect ratio,
  accounting for display rotation. In portrait the sensor's landscape ratio
  is inverted so the container becomes taller than wide."
  [sensor-ratio rotation]
  (when-let [^FrameLayout container @preview-container*]
    (let [width (.getWidth container)]
      (when (pos? width)
        (let [display-ratio (if (portrait-rotation? rotation)
                              (/ 1.0 (double sensor-ratio))
                              (double sensor-ratio))
              height (int (/ width display-ratio))
              lp (.getLayoutParams container)]
          (set! (.-height ^LinearLayout$LayoutParams lp) height)
          (.setLayoutParams container lp))))))

;; ---------------------------------------------------------------------------
;; CameraX binding
;; ---------------------------------------------------------------------------

(defn- ^ResolutionSelector build-resolution-selector [aspect-ratio-const]
  (.build
    (.setAspectRatioStrategy (ResolutionSelector$Builder.)
      (AspectRatioStrategy. (int aspect-ratio-const)
                            AspectRatioStrategy/FALLBACK_RULE_AUTO))))

(defn- bind-camera!
  "Binds CameraX preview and image-capture use cases to the activity lifecycle.
  Uses the sensor's native aspect ratio for both preview and capture."
  [^ProcessCameraProvider provider
   ^PreviewView preview-view
   ^LifecycleOwner owner]
  (.unbindAll provider)
  (let [ratio     (sensor-aspect-ratio provider)
        ar-const  (closest-camerax-ratio ratio)
        ^ResolutionSelector res-sel (build-resolution-selector ar-const)
        rotation  (.getRotation (.getDisplay preview-view))
        preview   (.build (doto (.setResolutionSelector (Preview$Builder.) res-sel)
                            (.setTargetRotation rotation)))
        capture   (.build (doto (.setResolutionSelector (ImageCapture$Builder.) res-sel)
                            (.setTargetRotation rotation)))
        cam-sel   CameraSelector/DEFAULT_BACK_CAMERA
        ^"[Landroidx.camera.core.UseCase;" use-cases
        (into-array UseCase [preview capture])]
    (.setSurfaceProvider ^Preview preview (.getSurfaceProvider preview-view))
    (.bindToLifecycle provider owner cam-sel use-cases)
    (resize-preview-container! ratio rotation)
    (reset! image-capture* capture)
    (reset! status* (format "Camera active. Sensor ratio: %.2f:1"
                            (double ratio)))))

(defn- start-camera!
  "Obtains the ProcessCameraProvider and binds the camera."
  [^View v]
  (let [ctx (.getContext v)
        future (ProcessCameraProvider/getInstance ctx)]
    (reset! status* "Starting camera...")
    (ensure-lifecycle-owner!)
    (.addListener future
                  (fn []
                    (let [provider (.get future)]
                      (reset! camera-provider* provider)
                      (on-ui
                        (when-let [pv @preview-view*]
                          (bind-camera! provider pv @lifecycle-owner*)))))
                  (ContextCompat/getMainExecutor ctx))))

;; ---------------------------------------------------------------------------
;; Permission + launch
;; ---------------------------------------------------------------------------

(defn- request-and-start! [^View v]
  (let [ctx (.getContext v)]
    (if (= PackageManager/PERMISSION_GRANTED
           (ContextCompat/checkSelfPermission ctx Manifest$permission/CAMERA))
      (start-camera! v)
      (act/request-permissions
        (ClojureActivity/getInstance "com.example.clojuredroid.main-activity")
        [Manifest$permission/CAMERA]
        (fn [_activity _permissions ^ints grant-results]
          (if (and (pos? (alength grant-results))
                   (= (aget grant-results 0) PackageManager/PERMISSION_GRANTED))
            (on-ui (start-camera! v))
            (reset! status* "Camera permission denied.")))))))

;; ---------------------------------------------------------------------------
;; Photo capture
;; ---------------------------------------------------------------------------

(defn- take-photo! [^View v]
  (when-let [capture @image-capture*]
    (let [ctx (.getContext v)
          filename (str "CLJ_" (System/currentTimeMillis) ".jpg")
          values (doto (ContentValues.)
                   (.put MediaStore$Images$Media/DISPLAY_NAME filename)
                   (.put MediaStore$Images$Media/MIME_TYPE "image/jpeg")
                   (.put MediaStore$Images$Media/RELATIVE_PATH "DCIM/ClojureDemo"))
          opts (.build (ImageCapture$OutputFileOptions$Builder.
                         (.getContentResolver ctx)
                         MediaStore$Images$Media/EXTERNAL_CONTENT_URI
                         values))]
      (reset! status* "Capturing...")
      (.takePicture ^ImageCapture capture opts
                    (ContextCompat/getMainExecutor ctx)
                    (proxy [ImageCapture$OnImageSavedCallback] []
                      (onCaptureStarted [])
                      (onImageSaved [_output]
                        (reset! status* (str "Saved: " filename)))
                      (onError [e]
                        (reset! status* (str "Capture failed: "
                                             (.getMessage e)))))))))

;; ---------------------------------------------------------------------------
;; Section UI
;; ---------------------------------------------------------------------------

(defn section-ui
  "Returns the Camera demo section UI tree."
  [ctx section-id]
  (let [label-color (get-theme-color ctx :text-color-secondary)]
    [:scroll-view {:id section-id
                   :layout-width :fill
                   :layout-height :fill
                   :visibility :gone}
     [:linear-layout {:orientation :vertical
                      :padding [16 16 16 16]
                      :layout-width :match-parent}
      [:text-view {:text "CameraX Preview"
                   :text-size [16 :sp]
                   :text-color label-color}]
      [:text-view {:text "Live camera preview using CameraX with photo capture."
                   :text-size [14 :sp]
                   :padding [0 4 0 12]}]

      ;; Preview container — height adjusted at bind time to match sensor ratio
      [:frame-layout {:layout-width :fill
                      :layout-height [300 :dp]
                      :background-color (unchecked-int 0xFF1A1A1A)
                      :on-create (fn [^FrameLayout fl]
                                   (reset! preview-container* fl))}
       [:view {:custom-constructor
               (fn [context & _args]
                 (let [pv (PreviewView. context)]
                   (reset! preview-view* pv)
                   (.setLayoutParams pv (FrameLayout$LayoutParams.
                                          FrameLayout$LayoutParams/MATCH_PARENT
                                          FrameLayout$LayoutParams/MATCH_PARENT))
                   pv))
               :layout-width :fill
               :layout-height :fill}]]

      ;; Controls
      [:linear-layout {:orientation :horizontal
                       :padding [0 12 0 0]
                       :layout-width :fill}
       [:button {:text "Start Camera"
                 :on-click (fn [^View v] (request-and-start! v))
                 :layout-width 0
                 :layout-weight 1}]
       [:button {:text "Take Photo"
                 :on-click (fn [^View v] (take-photo! v))
                 :layout-width 0
                 :layout-weight 1}]]

      ;; Status
      [:text-view {:text status*
                   :text-size [14 :sp]
                   :padding [0 8 0 0]}]]]))
