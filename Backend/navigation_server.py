from fastapi import FastAPI, UploadFile, File
from ultralytics import YOLO
from PIL import Image
from depth_helper import get_depth_map

import numpy as np
import cv2
import io

app = FastAPI()

print("Loading models...")

# COCO model
coco_model = YOLO("models/yolo11n.pt")

# Custom navigation model
navigation_model = YOLO("models/navigation_best.pt")

print("Models loaded!")



NAV_CLASSES = [
    "person",
    "chair",
    "dining table",
    "table",
    "bench",
]


def get_distance_label(area, depth):

    # Very close only if BOTH signals agree

    if area > 120000 and depth < 0.25:
        return "very close"

    # Near if either signal suggests closeness

    elif area > 60000 or depth < 0.40:
        return "near"

    else:
        return "far" 


@app.get("/")
def home():
    return {
        "message": "Navigation Server Running"
    }
    

@app.post("/navigate")
async def navigate(file: UploadFile = File(...)):


    contents = await file.read()

    image = Image.open(
        io.BytesIO(contents)
    ).convert("RGB")

    frame = np.array(image)

    frame = cv2.cvtColor(
        frame,
        cv2.COLOR_RGB2BGR
    )

    frame = cv2.rotate(
        frame,
        cv2.ROTATE_90_CLOCKWISE
    )

    cv2.imwrite(
        "debug_frame.jpg",
        frame
    )

    # ==========================================
    # MiDaS Depth
    # ==========================================

    depth_map = get_depth_map(frame)

    depth_norm = cv2.normalize(
        depth_map,
        None,
        0,
        1,
        cv2.NORM_MINMAX
    )

    # ==========================================
    # Detection
    # ==========================================

    coco_results = coco_model(
        frame,
        verbose=False
    )

    nav_results = navigation_model(
        frame,
        verbose=False
    )

    detections = []

    h, w = frame.shape[:2]

    # ==========================================
    # COCO detections
    # ==========================================

    for box in coco_results[0].boxes:

        conf = float(box.conf[0])

        if conf < 0.50:
            continue

        cls = int(box.cls[0])
        name = coco_results[0].names[cls]

        if name not in NAV_CLASSES:
            continue

        x1, y1, x2, y2 = box.xyxy[0]

        x1 = int(x1)
        y1 = int(y1)
        x2 = int(x2)
        y2 = int(y2)

        area = (x2 - x1) * (y2 - y1)
        
        if area < 10000:
            continue

        center_x = int((x1 + x2) / 2)

        if center_x < w * 0.33:
            position = "left"
        elif center_x < w * 0.66:
            position = "center"
        else:
            position = "right"

        roi = depth_norm[y1:y2, x1:x2]

        if roi.size > 0:
            depth_value = float(np.mean(roi))
        else:
            depth_value = 0.0

        detections.append({
            "class_id": cls,
            "class_name": name,
            "confidence": round(conf, 2),
            "position": position,
            "depth": round(depth_value, 2),
            "distance": get_distance_label(
              area,
              depth_value
            ),
            "area": area,
            "x1": x1,
            "y1": y1,
            "x2": x2,
            "y2": y2
        })

    # ==========================================
    # Door / Stairs / Window detections
    # ==========================================

    for box in nav_results[0].boxes:

        conf = float(box.conf[0])

        if conf < 0.60:
            continue

        cls = int(box.cls[0])
        name = nav_results[0].names[cls]

        x1, y1, x2, y2 = box.xyxy[0]

        x1 = int(x1)
        y1 = int(y1)
        x2 = int(x2)
        y2 = int(y2)

        area = (x2 - x1) * (y2 - y1)

        # remove tiny fake door detections
        if name == "door" and area < 10000:
            continue

        center_x = int((x1 + x2) / 2)

        if center_x < w * 0.33:
            position = "left"
        elif center_x < w * 0.66:
            position = "center"
        else:
            position = "right"

        roi = depth_norm[y1:y2, x1:x2]

        if roi.size > 0:
            depth_value = float(np.mean(roi))
        else:
            depth_value = 0.0

        detections.append({
            "class_id": cls,
            "class_name": name,
            "confidence": round(conf, 2),
            "position": position,
            "depth": round(depth_value, 2),
            "distance": get_distance_label(
                 area,
                 depth_value
               ),
            "area": area,
            "x1": x1,
            "y1": y1,
            "x2": x2,
            "y2": y2
        })
    
    # ==========================================
    #Safe Corridor Detection
    # ==========================================
    left_blocked = False
    center_blocked = False
    right_blocked = False

    for obj in detections:

     if obj["class_name"] in [
        "person",
        "chair",
        "dining table",
        "table",
        "bench",
    ]:

        if obj["distance"] in ["near", "very close"]:

            if obj["position"] == "left":
                left_blocked = True

            elif obj["position"] == "center":
                center_blocked = True

            else:
                right_blocked = True

    # ==========================================
    # Navigation Logic
    # ==========================================

    guidance = "Continue forward"

    # stairs have highest priority

    stairs = [
        obj for obj in detections
        if (
             obj["class_name"] == "stairs"
             and obj["confidence"] > 0.75
             and obj["area"] > 50000
         )
    ]

    if stairs:

       stair = max(
         stairs,
         key=lambda x: x["area"]
    )

       guidance = (
        f"Warning. Stairs on {stair['position']}. "
        f"{stair['distance']}."
    )

    else:

        persons = [
            obj for obj in detections
            if obj["class_name"] == "person"
            and obj["area"] > 30000
        ]

        if persons:

            person = max(
                persons,
                key=lambda x: x["area"]
            )

            if person["position"] == "center":

              guidance = (
               "Person ahead. "
               "Move left or right."
              )

            elif person["position"] == "left":

              guidance = (
               "Person on left. "
               "Move right."
              )

            else:

              guidance = (
               "Person on right. "
               "Move left."
              )

        else:

            obstacles = [
                obj for obj in detections
                if obj["class_name"] in [
                    "chair",
                    "dining table",
                    "table",
                    "bench",
                ]
                and obj["area"] > 30000
            ]

            if obstacles:

                obstacle = max(
                    obstacles,
                    key=lambda x: x["area"]
                )

                if obstacle["position"] == "center":

                    guidance = (
                        "Obstacle ahead. "
                        "Move left or right."
                    )

                elif obstacle["position"] == "left":

                    guidance = (
                        "Obstacle on left. "
                        "Move right."
                    )

                else:

                    guidance = (
                        "Obstacle on right. "
                        "Move left."
                    )

            else:

                doors = [
                    obj for obj in detections
                    if obj["class_name"] == "door"
                ]

                if doors:

                    door = max(
                        doors,
                        key=lambda x: x["area"]
                    )

                    if door["position"] == "center":

                       guidance = (
                        "Door ahead. "
                        "Continue forward."
                       )

                    elif door["position"] == "left":

                      guidance = (
                       "Door on left. "
                       "Continue forward."
                      )

                    else:

                      guidance = (
                       "Door on right. "
                       "Continue forward."
                      )
                    

                    
    print("\n---------------------")

    for obj in detections:

        print(
            f'{obj["class_name"]} | '
            f'{obj["position"]} | '
            f'conf={obj["confidence"]} | '
            f'depth={obj["depth"]} | '
            f'distance={obj["distance"]} | '
            f'area={obj["area"]}'
        )

    print(
            f"LEFT={left_blocked} "
            f"CENTER={center_blocked} "
            f"RIGHT={right_blocked}"
        )

    print("GUIDANCE:", guidance)
    # ==========================================
    # Risk Assessment
    # ==========================================

    max_risk_score = 0

    for obj in detections:

        # Ignore navigation targets
        if obj["class_name"] == "door":
            continue

        # Ignore weak detections
        if obj["confidence"] < 0.60:
            continue

        score = 0

        # --------------------------
        # Position
        # --------------------------

        if obj["position"] == "center":
            score += 3
        else:
            score += 1

        # --------------------------
        # Area (size)
        # --------------------------

        if obj["area"] > 100000:
            score += 3

        elif obj["area"] > 50000:
            score += 2

        elif obj["area"] > 15000:
            score += 1

        # --------------------------
        # Depth (MiDaS)
        # --------------------------

        if obj["depth"] < 0.25:
            score += 3

        elif obj["depth"] < 0.40:
            score += 2

        elif obj["depth"] < 0.60:
            score += 1

        # --------------------------
        # Object Type
        # --------------------------

        if obj["class_name"] == "stairs":

            score += 8

        elif obj["class_name"] == "person":

            score += 2

        elif obj["class_name"] in [
            "chair",
            "dining table",
            "table",
            "bench",
        ]:

            score += 2
        # --------------------------
        # Confidence
        # --------------------------
        
        if obj["confidence"] > 0.90:

              score += 2

        elif obj["confidence"] > 0.75:

              score += 1

        # Keep highest risk object

        max_risk_score = max(
            max_risk_score,
            score
        )

    # --------------------------
    # Convert Score → Risk Level
    # --------------------------

    if max_risk_score >= 12:

        risk = "critical"

    elif max_risk_score >= 9:

        risk = "high"

    elif max_risk_score >= 6:

        risk = "medium"

    else:

        risk = "low"
    

    print("RISK SCORE:", max_risk_score)
    print("RISK:", risk)


    return {
        "guidance": guidance,
        "risk": risk,
        "detections": detections
    }