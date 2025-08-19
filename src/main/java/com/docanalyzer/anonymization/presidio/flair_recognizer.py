from presidio_analyzer import EntityRecognizer, RecognizerResult
from flair.data import Sentence
from flair.models import SequenceTagger

class FlairRecognizer(EntityRecognizer):
    def __init__(self, model_path="flair/ner-english-large"):
        super().__init__(supported_entities=["PERSON", "LOCATION", "ORGANIZATION"], name="Flair Recognizer")
        self.tagger = SequenceTagger.load(model_path)

    def load(self):
        # No explicit loading needed as it's done in __init__
        pass

    def analyze(self, text, entities, nlp_artifacts=None):
        results = []
        sentence = Sentence(text)
        self.tagger.predict(sentence)
        for entity in sentence.get_spans('ner'):
            if entity.tag in self.supported_entities:
                results.append(RecognizerResult(
                    entity_type=entity.tag,
                    start=entity.start_pos,
                    end=entity.end_pos,
                    score=entity.score
                ))
        return results
