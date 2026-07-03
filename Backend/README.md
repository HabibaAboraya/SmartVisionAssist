# IndoorNavigator-Server

## AI-Powered Indoor Navigation Backend for Visually Impaired Users

IndoorNavigator-Server is the backend of the Smart Vision Assist project. It uses YOLOv11 object detection and MiDaS depth estimation to analyze indoor environments and provide real-time navigation assistance for visually impaired users.

The server is built using FastAPI and processes images captured by the Android application to detect obstacles, estimate their relative depth, calculate risk levels, and generate navigation guidance.

---

## Features

- Real-time object detection using YOLOv11
- Relative depth estimation using MiDaS
- Risk assessment based on:
  - Object type
  - Relative depth
  - Position in the frame
  - Detection confidence
- Indoor obstacle recognition
- REST API using FastAPI
- Designed for Android integration

---
## System Workflow

The following diagram illustrates the complete workflow of the Smart Vision Assist backend.

![System Workflow](images/system_workflow.png)

---

## Detection Results

The backend performs real-time object detection and relative depth estimation to provide navigation guidance for visually impaired users.

![Detection Results](images/detection_results.JPG)
---

## Project Structure

```
IndoorNavigator/
│
├── models/
│   ├── navigation_best.pt
│   └── yolo11n.pt
│
├── navigation_server.py
├── navigation_engine.py
├── depth_helper.py
├── requirements.txt
├── README.md
└── .gitignore
```

---

## Technologies Used

- Python
- FastAPI
- YOLOv11 (Ultralytics)
- MiDaS
- PyTorch
- OpenCV
- NumPy

---

## Installation

### 1. Clone the repository

```bash
git clone https://github.com/HabibaAboraya/IndoorNavigator-Server.git
cd IndoorNavigator-Server
```

### 2. Create a virtual environment (recommended)

```bash
python -m venv venv
```

Activate it:

**Windows**

```bash
venv\Scripts\activate
```

**macOS / Linux**

```bash
source venv/bin/activate
```

### 3. Install the required packages

```bash
pip install -r requirements.txt
```

---

## Install MiDaS

This repository does not include the MiDaS source code or pretrained weights.

Clone the official MiDaS repository inside the project folder:

```bash
git clone https://github.com/isl-org/MiDaS.git
```

Download the pretrained model:

```
midas_v21_small_256.pt
```

Place it inside:

```
MiDaS/weights/
```

The project expects the following structure:

```
IndoorNavigator/
│
├── MiDaS/
│   ├── midas/
│   ├── weights/
│   │   └── midas_v21_small_256.pt
│   └── run.py
```

---

## Running the Server

Start the FastAPI server:

```bash
uvicorn navigation_server:app --reload
```

The API will be available at:

```
http://127.0.0.1:8000
```

Interactive API documentation:

```
http://127.0.0.1:8000/docs
```

---

## AI Models

### YOLOv11

- COCO pretrained model (`yolo11n.pt`) for general indoor object detection
- Custom fine-tuned model (`navigation_best.pt`) trained on indoor navigation datasets containing different door and stair types

### MiDaS

Used to estimate **relative depth**, allowing the system to determine whether detected objects are near or far from the user.

---

## Risk Assessment

The server estimates the risk level of detected objects using multiple factors:

- Object type
- Relative depth
- Object position
- Detection confidence

These factors are combined to generate a navigation decision and appropriate user guidance.

---

## API Workflow

1. Android application captures an image.
2. Image is sent to the FastAPI server.
3. YOLOv11 detects indoor objects.
4. MiDaS estimates relative depth.
5. Risk assessment evaluates the scene.
6. Navigation guidance is generated.
7. Results are returned to the Android application.

---

## Future Improvements

- Improve YOLOv11 detection accuracy
- Enhance MiDaS depth estimation
- Add OCR for reading room numbers and signs
- Support outdoor navigation using GPS
- Conduct real-world testing with visually impaired users

---

## Author

**Habiba Abouraya**

Computer Science Student

Arab Academy for Science, Technology and Maritime Transport (AASTMT)

GitHub:
https://github.com/HabibaAboraya

---

## License

This project is intended for educational and research purposes.