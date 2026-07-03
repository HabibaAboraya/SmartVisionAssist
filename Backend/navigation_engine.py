from ultralytics import YOLO
from depth_helper import get_depth_map

import cv2
import numpy as np
import pyttsx3

# ==================================================
# Models
# ==================================================

coco_model = YOLO("yolo11n.pt")

door_model = YOLO(
    "Doors and Stairs.v1i.yolov11/runs/detect/train/weights/best.pt"
)

# ==================================================
# Voice Engine
# ==================================================

tts = pyttsx3.init()
last_message = ""

# ==================================================
# Camera
# ==================================================

cap = cv2.VideoCapture(0)

while True:

    ret, frame = cap.read()

    if not ret:
        break

    # ==================================================
    # Depth
    # ==================================================

    depth_map = get_depth_map(frame)

    depth_norm = cv2.normalize(
        depth_map,
        None,
        0,
        1,
        cv2.NORM_MINMAX
    )

    # ==================================================
    # Detection
    # ==================================================

    coco_results = coco_model(
        frame,
        verbose=False
    )

    door_results = door_model(
        frame,
        verbose=False
    )

    combined = frame.copy()

    combined = coco_results[0].plot(img=combined)
    combined = door_results[0].plot(img=combined)

    h, w = frame.shape[:2]

    detections = []

    # ==================================================
    # COCO Objects
    # ==================================================

    for box in coco_results[0].boxes:

        cls = int(box.cls[0])
        name = coco_results[0].names[cls]

        confidence = float(box.conf[0])

        if confidence < 0.50:
            continue

        x1, y1, x2, y2 = map(int, box.xyxy[0])

        center_x = int((x1 + x2) / 2)

        if center_x < w * 0.33:
            pos = "left"
        elif center_x < w * 0.66:
            pos = "center"
        else:
            pos = "right"

        roi = depth_norm[y1:y2, x1:x2]

        if roi.size > 0:
            depth_value = float(np.mean(roi))
        else:
            depth_value = 0.0

        detections.append(
            (name, pos, depth_value)
        )

        print(
            f"{name} | {pos} | depth={depth_value:.2f}"
        )

    # ==================================================
    # Doors / Stairs
    # ==================================================

for box in door_results[0].boxes:

    confidence = float(box.conf[0])

    if confidence < 0.80:
        continue

    cls = int(box.cls[0])

    name = door_results[0].names[cls]

    print(f"{name} | conf={confidence:.2f}")

    x1, y1, x2, y2 = map(int, box.xyxy[0])

    center_x = int((x1 + x2) / 2)

    if center_x < w * 0.33:
        pos = "left"
    elif center_x < w * 0.66:
        pos = "center"
    else:
        pos = "right"

    roi = depth_norm[y1:y2, x1:x2]

    if roi.size > 0:
        depth_value = float(np.mean(roi))
    else:
        depth_value = 0.0

    detections.append(
        (name, pos, depth_value)
    )

    print(
        f"{name} | {pos} | depth={depth_value:.2f}"
    )
   

    # ==================================================
    # Risk Assessment
    # ==================================================

    guidance = None

    for obj, pos, depth in detections:

        # Stairs = highest priority
        if (
            obj == "stairs"
            and pos == "center"
            and depth > 0.40
        ):
            guidance = "Warning. Stairs ahead."
            break

        # Person collision
        elif (
            obj == "person"
            and pos == "center"
            and depth > 0.40
        ):
            guidance = "Person ahead. Move slightly left."
            break

        # Object collision
        elif (
            obj in ["chair", "table", "sofa"]
            and pos == "center"
            and depth > 0.40
        ):
            guidance = "Obstacle ahead. Move slightly right."
            break

    # ==================================================
    # Voice Warning
    # ==================================================

    if (
        guidance is not None
        and guidance != last_message
    ):

        print("WARNING:", guidance)

        try:
            tts.say(guidance)
            tts.runAndWait()
        except Exception as e:
            print("Voice Error:", e)

        last_message = guidance

    # ==================================================
    # Display Warning
    # ==================================================

    if guidance is not None:

        cv2.putText(
            combined,
            guidance,
            (20, 40),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.8,
            (0, 0, 255),
            2
        )

    cv2.imshow(
        "Indoor Navigation Assistant",
        combined
    )

    if cv2.waitKey(1) & 0xFF == ord("q"):
        break

cap.release()
cv2.destroyAllWindows()