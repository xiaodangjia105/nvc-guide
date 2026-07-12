import { useState, useCallback } from 'react';

const USER_ID_KEY = 'nvc_user_id';

function generateUserId(): number {
  return Math.floor(Math.random() * 900000) + 100000;
}

export function useUserId(): [number, (id: number) => void] {
  const [userId, setUserIdState] = useState<number>(() => {
    const stored = localStorage.getItem(USER_ID_KEY);
    if (stored) {
      const parsed = parseInt(stored, 10);
      if (!isNaN(parsed)) return parsed;
    }
    const newId = generateUserId();
    localStorage.setItem(USER_ID_KEY, String(newId));
    return newId;
  });

  const setUserId = useCallback((id: number) => {
    localStorage.setItem(USER_ID_KEY, String(id));
    setUserIdState(id);
  }, []);

  return [userId, setUserId];
}
