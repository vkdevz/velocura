import React, { useState, useEffect, useRef } from 'react';
import { Mic, MicOff, AlertCircle, Volume2, Sparkles } from 'lucide-react';
import { Button } from '../ui/Button';

export const VoiceDictationButton = ({
  onTranscript,
  className = '',
  label = 'Voice Dictation',
  compact = false,
  placeholderHint = 'Speak clearly into your microphone...'
}) => {
  const [isListening, setIsListening] = useState(false);
  const [status, setStatus] = useState('idle'); // idle, listening, error, unsupported
  const [errorMessage, setErrorMessage] = useState('');
  const [interimTranscript, setInterimTranscript] = useState('');
  
  const recognitionRef = useRef(null);
  const isIntentionalStop = useRef(false);

  useEffect(() => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setStatus('unsupported');
    }

    return () => {
      isIntentionalStop.current = true;
      if (recognitionRef.current) {
        try {
          recognitionRef.current.stop();
        } catch (e) {}
      }
    };
  }, []);

  const startListening = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setStatus('unsupported');
      setErrorMessage('Voice-to-text API is not supported in this browser. Please use Chrome, Edge, or Safari.');
      return;
    }

    isIntentionalStop.current = false;
    setErrorMessage('');
    setInterimTranscript('');

    try {
      if (recognitionRef.current) {
        try { recognitionRef.current.abort(); } catch (e) {}
      }

      const recognition = new SpeechRecognition();
      recognition.continuous = true;
      recognition.interimResults = true;
      recognition.lang = navigator.language || 'en-US';

      recognition.onstart = () => {
        setIsListening(true);
        setStatus('listening');
        setErrorMessage('');
      };

      recognition.onresult = (event) => {
        let currentInterim = '';
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const transcriptChunk = event.results[i][0].transcript;
          if (event.results[i].isFinal) {
            if (onTranscript) {
              onTranscript(transcriptChunk.trim());
            }
            setInterimTranscript('');
          } else {
            currentInterim += transcriptChunk;
          }
        }
        if (currentInterim) {
          setInterimTranscript(currentInterim);
        }
      };

      recognition.onerror = (event) => {
        console.error('Speech recognition engine event:', event.error);
        if (event.error === 'not-allowed' || event.error === 'permission-denied') {
          setErrorMessage('Microphone access blocked. Click browser lock icon to allow mic.');
          setStatus('error');
          setIsListening(false);
          isIntentionalStop.current = true;
        } else if (event.error === 'no-speech') {
          // Silent pause - ignore error, let onend handle restart if needed
        } else if (event.error !== 'aborted') {
          setErrorMessage(`Speech recognition error (${event.error})`);
          setStatus('error');
        }
      };

      recognition.onend = () => {
        setInterimTranscript('');
        // Auto-reconnect if speech ended due to pause timeout rather than user manual stop
        if (!isIntentionalStop.current && status !== 'error') {
          try {
            recognition.start();
            return;
          } catch (e) {
            console.warn('Auto-restart recognition attempt:', e);
          }
        }
        setIsListening(false);
        if (status === 'listening') {
          setStatus('idle');
        }
      };

      recognitionRef.current = recognition;
      recognition.start();
    } catch (err) {
      console.error('Failed to launch microphone session:', err);
      setStatus('error');
      setErrorMessage('Microphone session failed to initialize.');
      setIsListening(false);
    }
  };

  const stopListening = () => {
    isIntentionalStop.current = true;
    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch (e) {}
    }
    setIsListening(false);
    setInterimTranscript('');
    setStatus('idle');
  };

  const toggleDictation = () => {
    if (isListening) {
      stopListening();
    } else {
      startListening();
    }
  };

  if (status === 'unsupported') {
    return (
      <div className={`inline-flex items-center space-x-1.5 text-[11px] text-slate-400 font-mono ${className}`}>
        <MicOff className="w-3.5 h-3.5 text-amber-400/80" />
        <span>Voice Unsupported (Use Chrome/Edge)</span>
      </div>
    );
  }

  if (compact) {
    return (
      <div className={`inline-flex items-center gap-2 ${className}`}>
        <button
          type="button"
          onClick={toggleDictation}
          className={`p-2 rounded-lg transition-all cursor-pointer flex items-center justify-center ${
            isListening
              ? 'bg-red-500/20 text-red-400 border border-red-500/40 animate-pulse shadow-lg shadow-red-500/10'
              : 'bg-slate-900 text-slate-300 hover:text-white hover:bg-slate-800 border border-slate-700'
          }`}
          title={isListening ? 'Stop Voice Dictation' : 'Click to Speak (Voice-to-Text)'}
        >
          {isListening ? <MicOff className="w-4 h-4" /> : <Mic className="w-4 h-4 text-cyan-400" />}
        </button>

        {isListening && (
          <div className="flex items-center gap-2 bg-slate-900/90 border border-cyan-500/30 px-3 py-1 rounded-lg">
            <div className="flex items-center gap-0.5">
              <span className="w-1 h-3 bg-cyan-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
              <span className="w-1 h-4 bg-cyan-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
              <span className="w-1 h-2 bg-cyan-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
            </div>
            <span className="text-xs font-mono text-cyan-300 max-w-[200px] truncate">
              {interimTranscript || 'Listening...'}
            </span>
          </div>
        )}

        {status === 'error' && (
          <span className="inline-flex items-center gap-1 text-[11px] font-mono text-red-400">
            <AlertCircle className="w-3.5 h-3.5" />
            {errorMessage || 'Mic Error'}
          </span>
        )}
      </div>
    );
  }

  return (
    <div className={`inline-flex flex-wrap items-center gap-2.5 ${className}`}>
      <Button
        type="button"
        variant={isListening ? 'danger' : 'secondary'}
        size="sm"
        onClick={toggleDictation}
        className={`relative ${isListening ? 'animate-pulse border-red-500/50' : ''}`}
      >
        {isListening ? (
          <>
            <span className="w-2 h-2 rounded-full bg-red-500 animate-ping mr-1.5" />
            <MicOff className="w-3.5 h-3.5 mr-1" />
            Stop Dictation
          </>
        ) : (
          <>
            <Mic className="w-3.5 h-3.5 mr-1 text-cyan-400" />
            {label}
          </>
        )}
      </Button>

      {isListening && (
        <div className="inline-flex items-center gap-2 text-xs font-mono text-cyan-300 bg-cyan-950/60 px-3 py-1.5 rounded-lg border border-cyan-500/30">
          <div className="flex items-center gap-0.5">
            <span className="w-1 h-3.5 bg-cyan-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
            <span className="w-1 h-4.5 bg-cyan-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
            <span className="w-1 h-2.5 bg-cyan-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
          </div>
          <span className="max-w-[280px] truncate italic text-slate-200">
            {interimTranscript ? `"${interimTranscript}"` : placeholderHint}
          </span>
        </div>
      )}

      {status === 'error' && (
        <span className="inline-flex items-center gap-1 text-xs font-mono text-red-400 bg-red-950/40 px-2.5 py-1 rounded border border-red-500/20">
          <AlertCircle className="w-3.5 h-3.5" />
          {errorMessage || 'Dictation Error'}
        </span>
      )}
    </div>
  );
};
