/* ACL 启动器官网 · 极简脚本（无依赖） */
(function () {
  'use strict';

  /* 1. 点击校验值区域复制 SHA-256 */
  var hashBox = document.querySelector('.hash');
  if (hashBox && navigator.clipboard) {
    hashBox.style.cursor = 'pointer';
    hashBox.title = '点击复制校验信息';
    hashBox.addEventListener('click', function () {
      navigator.clipboard.writeText(hashBox.innerText.trim()).then(function () {
        var old = hashBox.style.borderColor;
        hashBox.style.borderColor = '#35e07a';
        setTimeout(function () { hashBox.style.borderColor = old; }, 600);
      });
    });
  }

  /* 2. 锚点跳转补偿固定顶栏高度 */
  var header = document.querySelector('.site-header');
  document.querySelectorAll('a[href^="#"]').forEach(function (a) {
    a.addEventListener('click', function (e) {
      var id = a.getAttribute('href');
      if (id === '#' || id.length < 2) return;
      var target = document.querySelector(id);
      if (!target) return;
      e.preventDefault();
      var offset = header ? header.offsetHeight + 12 : 12;
      var top = target.getBoundingClientRect().top + window.pageYOffset - offset;
      window.scrollTo({ top: top, behavior: 'smooth' });
      history.replaceState(null, '', id);
    });
  });

  /* 3. 导航高亮当前区块 */
  var links = Array.prototype.slice.call(document.querySelectorAll('.nav-links a'));
  var sections = links
    .map(function (l) { return document.querySelector(l.getAttribute('href')); })
    .filter(Boolean);

  if ('IntersectionObserver' in window && sections.length) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        links.forEach(function (l) {
          var on = l.getAttribute('href') === '#' + entry.target.id;
          l.style.color = on ? 'var(--text)' : '';
        });
      });
    }, { rootMargin: '-45% 0px -50% 0px' });
    sections.forEach(function (s) { io.observe(s); });
  }

  /* 4. 下载按钮点击埋点（仅本地 console，方便以后接统计） */
  document.querySelectorAll('a[download]').forEach(function (a) {
    a.addEventListener('click', function () {
      console.info('[ACL] download:', a.getAttribute('href'));
    });
  });
})();
