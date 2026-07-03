import torch
import cv2
import numpy as np
import sys

sys.path.append("MiDaS")

from midas.model_loader import load_model

device = torch.device("cpu")

model, transform, net_w, net_h = load_model(
    device,
    "MiDaS/weights/midas_v21_small_256.pt",
    "midas_v21_small_256",
    optimize=False
)

def get_depth_map(frame):

    img = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

    sample = transform({"image": img})

    img_input = sample["image"]

    # numpy -> torch tensor
    img_input = torch.from_numpy(img_input).float()

    # add batch dimension
    img_input = img_input.unsqueeze(0)

    img_input = img_input.to(device)

    with torch.no_grad():

        prediction = model(img_input)

        prediction = torch.nn.functional.interpolate(
            prediction.unsqueeze(1),
            size=img.shape[:2],
            mode="bicubic",
            align_corners=False,
        ).squeeze()

    depth = prediction.cpu().numpy()

    return depth