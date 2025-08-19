FROM mcr.microsoft.com/presidio-analyzer:latest

# Install flair and flask
RUN pip install flair flask

# Copy the custom recognizer and the controller
COPY flair_recognizer.py /usr/local/lib/python3.8/site-packages/
COPY presidio_anonymizer_controller.py /usr/local/lib/python3.8/site-packages/

# Run the controller
CMD ["python", "/usr/local/lib/python3.8/site-packages/presidio_anonymizer_controller.py"]
