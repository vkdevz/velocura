import React from 'react';
import { AlertTriangle, RefreshCw, Home } from 'lucide-react';
import { Button } from '../ui/Button';
import { Card, CardHeader, CardTitle, CardContent } from '../ui/Card';

export class ShellErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, errorInfo: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('ShellErrorBoundary caught an error:', error, errorInfo);
    this.setState({ errorInfo });
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null, errorInfo: null });
    if (this.props.onReset) {
      this.props.onReset();
    } else {
      window.location.reload();
    }
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="p-6 max-w-4xl mx-auto space-y-6">
          <Card className="border-red-500/30 bg-red-950/10">
            <CardHeader>
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-red-500/10 border border-red-500/20 text-red-400 flex items-center justify-center">
                  <AlertTriangle className="w-5 h-5" />
                </div>
                <div>
                  <CardTitle subtitle="Clinical Workstation Exception Handler">
                    Workstation Rendering Error
                  </CardTitle>
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-xs text-slate-300 font-sans leading-relaxed">
                An unexpected error occurred while rendering this workspace section. The main application shell remains active and safe.
              </p>
              {this.state.error && (
                <div className="p-3 bg-slate-950 rounded-lg border border-slate-800 font-mono text-[11px] text-red-300 overflow-x-auto">
                  {this.state.error.toString()}
                </div>
              )}
              <div className="flex flex-wrap items-center gap-3 pt-2">
                <Button
                  variant="primary"
                  size="sm"
                  onClick={this.handleReset}
                  icon={RefreshCw}
                >
                  Reload Workspace Section
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => (window.location.href = '/')}
                  icon={Home}
                >
                  Return to Home
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      );
    }

    return this.props.children;
  }
}
