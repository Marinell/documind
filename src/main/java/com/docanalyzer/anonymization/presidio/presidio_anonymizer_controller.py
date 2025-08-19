from flask import Flask, request, jsonify
from presidio_analyzer import AnalyzerEngine
from flair_recognizer import FlairRecognizer
import importlib.util
import sys

app = Flask(__name__)
analyzer = AnalyzerEngine()

@app.route('/add_recognizer', methods=['POST'])
def add_recognizer():
    data = request.get_json()
    path = data.get('path')
    if not path:
        return jsonify({"error": "Path to recognizer not provided"}), 400

    try:
        spec = importlib.util.spec_from_file_location("flair_recognizer", path)
        module = importlib.util.module_from_spec(spec)
        sys.modules["flair_recognizer"] = module
        spec.loader.exec_module(module)

        flair_recognizer = module.FlairRecognizer()
        analyzer.registry.add_recognizer(flair_recognizer)

        return jsonify({"message": "Flair recognizer added successfully"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=3000)
