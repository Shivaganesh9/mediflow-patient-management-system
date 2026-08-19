import { useCallback, useEffect, useRef, useState, DependencyList } from 'react';
import { ApiError } from '../types/common';

interface AsyncResult<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  fieldErrors: Record<string, string> | null;
  reload: () => void;
  setData: (value: T | null) => void;
}

export function useAsync<T>(task: () => Promise<T>, deps: DependencyList = []): AsyncResult<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string> | null>(null);
  const taskRef = useRef(task);
  taskRef.current = task;

  const reload = useCallback(() => {
    setLoading(true);
    setError(null);
    setFieldErrors(null);
    taskRef.current()
      .then((result) => {
        setData(result);
        setLoading(false);
      })
      .catch((err: unknown) => {
        setLoading(false);
        if (err instanceof ApiError) {
          setError(err.message);
          setFieldErrors(err.errors ?? null);
        } else {
          setError('An unexpected error occurred. Please try again.');
        }
      });
  }, []);

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reload, ...deps]);

  return { data, loading, error, fieldErrors, reload, setData };
}