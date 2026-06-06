# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Vue 3 + TypeScript frontend for an Online Judge (OJ) system. The application is a modern web-based programming competition platform with features including problem browsing, code submission, contest management, user authentication, and AI assistance.

## Architecture & Key Technologies

### Core Stack
- **Vue 3** with Composition API and `<script setup>` syntax
- **TypeScript** for type safety
- **Vue Router 4** for navigation
- **Vuex 4** (with Pinia also installed) for state management
- **Arco Design Vue** as the UI component library

### Key Dependencies
- **Monaco Editor** - Code editor component
- **Axios** - HTTP client with OpenAPI-generated services
- **ECharts** - Data visualization (language statistics, heatmaps)
- **ByteMD** - Markdown editor with KaTeX support for math formulas
- **Highlight.js** - Code syntax highlighting

### Project Structure
```
src/
├── access/              # Authentication & authorization
│   ├── ACCESS_ENUM.ts   # Permission definitions
│   ├── checkAccess.ts   # Access control logic
│   └── index.ts         # Router guards
├── components/          # Reusable UI components
│   ├── CodeEditor.vue   # Monaco-based code editor
│   ├── GlobalHeader.vue # Main navigation header
│   ├── ThemeSwitcher.vue
│   └── ...
├── layouts/             # Page layout components
├── router/
│   ├── index.ts         # Router configuration
│   └── routes.ts        # Route definitions with access control
├── store/               # Vuex store modules
│   ├── index.ts
│   └── user.ts          # User authentication state
├── views/               # Page components
│   ├── question/        # Problem-related views
│   ├── contest/         # Contest-related views
│   └── user/            # User authentication views
├── generated/           # OpenAPI-generated API clients
└── main.ts              # Application entry point
```

## Common Development Commands

### Project Setup
```bash
npm install
```

### Development Server
```bash
npm run serve          # Start dev server on port 8080
```

### Production Build
```bash
npm run build          # Compile and minify for production
```

### Linting
```bash
npm run lint           # ESLint + Prettier code quality check
```

## Key Features & Implementation Details

### Authentication System
- **ACCESS_ENUM**: Defines three permission levels - `NOT_LOGIN`, `USER`, `ADMIN`
- **Router Guards**: Automatic redirection based on user authentication state
- **Token-based**: Uses Authorization header with Bearer tokens
- **User Roles**: Stored in Vuex store and checked against route metadata

### Code Editor
- **Monaco Editor**: Full-featured IDE-like editor supporting multiple languages
- **Language Support**: C++, JavaScript, TypeScript, HTML, CSS, JSON
- **Theming**: Dark/light themes with customizable options
- **Features**: Syntax highlighting, autocomplete, parameter hints, code folding

### API Integration
- **OpenAPI Generated**: All API clients are auto-generated in `generated/services/`
- **Axios-based**: Uses configured axios client with error handling
- **Proxy Setup**: Dev server proxies `/api` to backend at `http://localhost:8101`
- **Type Safety**: Full TypeScript types for all API requests/responses

### UI/UX Features
- **Responsive Design**: Arco Design components with mobile considerations
- **Theme Switching**: Light/dark mode support
- **Real-time Updates**: Monaco editor with reactive model binding
- **Markdown Support**: ByteMD editor with math formula rendering

## Routing & Navigation

Routes are defined in `src/router/routes.ts` with metadata for access control:
```typescript
{
  path: "/submission",
  name: "评测记录",
  component: () => import("@/views/SubmissionListView.vue"),
  meta: {
    access: ACCESS_ENUM.USER,  // Requires logged-in user
  },
}
```

## State Management

- **Vuex Store**: Centralized state management in `src/store/`
- **User Module**: Handles authentication state and user profile
- **Pinia**: Also available for new features (installed but Vuex is primary)

## Development Guidelines

### Code Style
- Use Composition API with `<script setup>` syntax for new components
- Follow TypeScript best practices with explicit types
- Use Arco Design Vue components for consistent UI
- Implement proper error handling with try/catch blocks

### Access Control
- Use `checkAccess()` utility for permission checks
- Add `access` metadata to routes for automatic protection
- Conditionally render UI elements based on user roles

### API Usage
- Import from `generated/services/` for type-safe API calls
- Handle loading states and errors gracefully
- Use the configured axios client for consistent behavior

### Component Patterns
- Use props/events for parent-child communication
- Leverage computed properties for reactive data
- Use watchers for side effects on reactive data
- Implement proper cleanup in `onBeforeUnmount`

## Configuration Files

- **vue.config.js**: Webpack configuration with Monaco editor plugin
- **tsconfig.json**: TypeScript compiler options
- **.eslintrc.js**: ESLint rules with Prettier integration
- **babel.config.js**: Babel presets and plugins

## Environment Setup
- Dev server runs on `http://localhost:8080`
- API proxy configured to `http://localhost:8101`
- OpenAPI spec generates TypeScript clients automatically

This is a well-structured Vue 3 application with comprehensive type safety, modern development practices, and a clean architecture separating concerns between UI, state management, and API integration.