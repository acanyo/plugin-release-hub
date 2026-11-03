<script lang="ts">
    let { name, size = 18 }: { name: string; size?: number } = $props()
    const paths: Record<string, string> = {
        arrow: 'M7 17 17 7M7 7h10v10',
        back: 'm12 19-7-7 7-7M5 12h14',
        bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',
        search: 'm21 21-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
        rss: 'M4 11a9 9 0 0 1 9 9M4 4a16 16 0 0 1 16 16M4 20h.01',
        mail: 'M3 5h18v14H3zM3 6l9 7 9-7',
        moon: 'M21 13a9 9 0 0 1-10-10 9 9 0 1 0 10 10Z',
        sun: 'M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5M17.5 17.5 19 19M5 19l1.5-1.5M17.5 6.5 19 5M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
        close: 'm6 6 12 12M6 18 18 6',
        download: 'M12 3v12m-5-5 5 5 5-5M4 17v4h16v-4',
        tag: 'M20 13 13 20a2 2 0 0 1-3 0L2 12V2h10l8 8a2 2 0 0 1 0 3ZM7 7h.01',
        grid: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
        check: 'm5 12 4 4L19 6',
        code: 'm8 7-5 5 5 5m8-10 5 5-5 5M14 4l-4 16',
        plus: 'M12 5v14M5 12h14',
        refresh: 'M20 7v5h-5M4 17v-5h5M6 6a8 8 0 0 1 13 3M18 18A8 8 0 0 1 5 15',
        link: 'M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-2 2M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l2-2',
        box: 'm12 3 9 5v8l-9 5-9-5V8l9-5ZM3 8l9 5 9-5M12 13v8M7.5 5.5l9 5',
        repository:
            'M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 3H20v19H6.5A2.5 2.5 0 0 1 4 19.5v-14A2.5 2.5 0 0 1 6.5 3ZM8 7h7M8 10h5',
        calendar: 'M8 2v4m8-4v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2Z',
        activity: 'M3 12h4l3-8 4 16 3-8h4',
        history: 'M3 11a9 9 0 1 1 2 7M3 4v7h7M12 7v5l3 2',
        users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M16 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.87M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
        settings:
            'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8ZM9 3l-.5 2-2 .8-1.9-.6-2 3.5L4 10v2l-1.4 1.3 2 3.5 1.9-.6 2 .8L9 20h4l.5-2 2-.8 1.9.6 2-3.5L18 13v-2l1.4-1.3-2-3.5-1.9.6-2-.8L13 3H9Z',
        chevron: 'm9 5 7 7-7 7',
        info: 'M12 11v6M12 7h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
        shield: 'M12 3 3 7v5c0 5 9 10 9 10s9-5 9-10V7l-9-4Zm-4 9 3 3 5-5',
        edit: 'm16 3 5 5-12 12H4v-5L16 3Zm-3 3 5 5',
    }
</script>

<svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.65"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"><path d={paths[name] || paths.code} /></svg
>
