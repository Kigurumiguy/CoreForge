# CoreForge

## AI Memory and Emotion Core

### Overview

CoreForge is the foundational AI memory and emotion core system designed to power [Pixel Haven](https://github.com/Kigurumiguy/PixelHaven). It serves as the central cognitive architecture that enables persistent memory, emotional processing, and contextual awareness for AI characters within the Pixel Haven ecosystem.

### Key Features

#### Memory Architecture
- **Long-term Memory Storage**: Persistent storage of experiences, interactions, and learned behaviors
- **Short-term Working Memory**: Active context management for ongoing conversations and tasks
- **Episodic Memory**: Timestamped event storage with contextual metadata
- **Semantic Memory**: Structured knowledge graphs of facts, relationships, and concepts

#### Emotional Processing
- **Emotion State Machine**: Dynamic emotional states that respond to interactions and events
- **Mood Tracking**: Persistent mood changes based on accumulated experiences
- **Emotional Context**: Influences decision-making and response generation
- **Empathy Modeling**: Understanding and responding to user emotional states

#### Core Functions
- **Context Awareness**: Maintains awareness of ongoing situations and historical context
- **Memory Consolidation**: Processes and organizes experiences for efficient retrieval
- **Adaptive Learning**: Learns from interactions to improve responses over time
- **Personality Integration**: Consistent personality traits informed by memory and emotion

### Integration with Pixel Haven

CoreForge powers the AI characters in Pixel Haven by providing:
- Consistent character memory across sessions
- Emotionally coherent responses
- Character development over time
- Relationship tracking between characters and users

### Technical Architecture

```
┌─────────────────────────────────────┐
│         Pixel Haven                 │
│      (Frontend Interface)           │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│         CoreForge Core              │
├─────────────────────────────────────┤
│  • Memory Manager                   │
│  • Emotion Engine                   │
│  • Context Processor                │
│  • Learning System                  │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│      Data Storage Layer             │
│  • Vector Database (Embeddings)     │
│  • Relational DB (Structured Data)  │
│  • Time-series DB (Events/Emotions) │
└─────────────────────────────────────┘
```

### Getting Started

```bash
# Clone the repository
git clone https://github.com/Kigurumiguy/CoreForge.git

# Install dependencies
cd CoreForge
npm install

# Configure environment
cp .env.example .env

# Run tests
npm test

# Start the core system
npm start
```

### Configuration

CoreForge can be configured via environment variables or a configuration file:

```yaml
memory:
  max_working_memory: 10000  # tokens
  consolidation_interval: 3600  # seconds
  retention_policy: "6months"

emotion:
  mood_decay_rate: 0.05
  emotion_intensity_threshold: 0.7
  default_emotional_state: "neutral"

learning:
  adaptation_rate: 0.01
  experience_replay_enabled: true
```

### API Reference

#### Memory Operations
```javascript
// Store a memory
coreForge.memory.store({
  type: 'episodic',
  content: 'User shared their favorite color is blue',
  timestamp: Date.now(),
  tags: ['preference', 'color']
});

// Retrieve memories
const memories = await coreForge.memory.recall({
  query: 'favorite color',
  limit: 5
});
```

#### Emotion Updates
```javascript
// Update emotional state
coreForge.emotion.update({
  event: 'positive_interaction',
  intensity: 0.8
});

// Get current emotional state
const emotionalState = coreForge.emotion.getState();
```

### Use Cases

1. **Interactive AI Companions**: Characters that remember past conversations and develop relationships
2. **Adaptive NPCs**: Game characters that learn from player behavior
3. **Personalized Assistants**: AI helpers that understand user preferences and context
4. **Educational Tutors**: Systems that track learning progress and adapt teaching methods

### Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### Roadmap

- [ ] Multi-character memory isolation and sharing
- [ ] Advanced emotion blending and complex states
- [ ] Memory importance scoring and forgetting curves
- [ ] Cross-session memory transfer protocols
- [ ] Emotion visualization dashboard
- [ ] Memory debugging and inspection tools

### Related Projects

- [Pixel Haven](https://github.com/Kigurumiguy/PixelHaven) - The primary application using CoreForge

### License

MIT License - see [LICENSE](LICENSE) file for details

### Contact

For questions or support, please open an issue on GitHub.

---

**Note**: CoreForge is designed to work seamlessly with Pixel Haven but can be adapted for other AI character systems requiring memory and emotional capabilities.
