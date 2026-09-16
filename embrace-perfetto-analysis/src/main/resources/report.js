/*
 * Drawing shared by every report page. Each page inlines this ahead of its own script, so a report
 * stays one self-contained file: what differs between pages is their column descriptors and what
 * they put in their tiles, not how a table or a bar is drawn.
 */
var REPORT_UI = (function () {
  'use strict';

  function duration(nanos) {
    if (nanos < 1e3) { return Math.round(nanos) + ' ns'; }
    if (nanos < 1e6) { return (nanos / 1e3).toFixed(2) + ' µs'; }
    if (nanos < 1e9) { return (nanos / 1e6).toFixed(2) + ' ms'; }
    return (nanos / 1e9).toFixed(2) + ' s';
  }

  /** Keeps a share that rounds away at two places, since most sections hold a thousandth of the window. */
  function share(percent) {
    if (percent >= 10) { return percent.toFixed(1) + '%'; }
    if (percent >= 0.1) { return percent.toFixed(2) + '%'; }
    return percent.toFixed(3) + '%';
  }

  function bytes(count) {
    if (count < 1024) { return count + ' bytes'; }
    if (count < 1048576) { return (count / 1024).toFixed(1) + ' KiB'; }
    return (count / 1048576).toFixed(1) + ' MiB';
  }

  /** A counter that tallies bytes reads as a size; one that tallies files reads as the count it is. */
  function talliesBytes(counter) {
    return counter.name.indexOf('bytes') >= 0;
  }

  function formatCell(kind, held) {
    if (kind === 'duration') { return duration(held); }
    if (kind === 'percent') { return share(held); }
    if (kind === 'bytes') { return bytes(held); }
    if (held === null || held === undefined) { return '-'; }
    if (kind === 'count' || kind === 'tally') { return held.toLocaleString(); }
    return String(held);
  }

  function counterCell(counter, column) {
    var held = counter[column.key];
    if (column.kind === 'tids') { return held.join(', '); }
    if (column.kind === 'tally' && talliesBytes(counter)) { return bytes(held); }
    return formatCell(column.kind, held);
  }

  /** Marks the matched run so a hit is visible in a long section name, without trusting it as markup. */
  function highlight(target, text, query) {
    var at = query ? text.toLowerCase().indexOf(query.toLowerCase()) : -1;
    if (at < 0) {
      target.textContent = text;
      return;
    }
    var hit = document.createElement('mark');
    hit.textContent = text.substr(at, query.length);
    target.appendChild(document.createTextNode(text.substr(0, at)));
    target.appendChild(hit);
    target.appendChild(document.createTextNode(text.substr(at + query.length)));
  }

  function drawTiles(holder, tiles) {
    holder.textContent = '';
    tiles.forEach(function (tile) {
      var box = document.createElement('div');
      box.className = 'tile';
      var value = document.createElement('div');
      value.className = 'value';
      value.textContent = tile.value;
      var label = document.createElement('div');
      label.className = 'label';
      label.textContent = tile.label;
      box.appendChild(value);
      box.appendChild(label);
      holder.appendChild(box);
    });
  }

  /** A list of names, or the empty note in its place when there are none. */
  function drawList(listEl, emptyEl, names) {
    listEl.textContent = '';
    listEl.hidden = names.length === 0;
    emptyEl.hidden = !listEl.hidden;
    names.forEach(function (name) {
      var item = document.createElement('li');
      item.textContent = name;
      listEl.appendChild(item);
    });
  }

  /** Column headings. Passing no onSort leaves a table that does not sort, as a counters table is. */
  function drawHeader(headEl, columns, sortBy, descending, onSort) {
    headEl.textContent = '';
    columns.forEach(function (column) {
      var th = document.createElement('th');
      th.textContent = column.label;
      if (sortBy && column === sortBy) {
        var arrow = document.createElement('span');
        arrow.className = 'arrow';
        arrow.textContent = descending ? ' ▼' : ' ▲';
        th.appendChild(arrow);
      }
      if (onSort) {
        th.addEventListener('click', function () { onSort(column); });
      }
      headEl.appendChild(th);
    });
  }

  function drawRows(bodyEl, rows, columns, cellOf, query) {
    bodyEl.textContent = '';
    rows.forEach(function (row) {
      var tr = document.createElement('tr');
      columns.forEach(function (column) {
        var td = document.createElement('td');
        if (column.kind === 'text') {
          highlight(td, cellOf(row, column), query || '');
        } else {
          td.textContent = cellOf(row, column);
        }
        tr.appendChild(td);
      });
      bodyEl.appendChild(tr);
    });
  }

  /** A whole table that neither sorts nor filters. */
  function drawTable(headEl, bodyEl, rows, columns, cellOf) {
    drawHeader(headEl, columns, null, false, null);
    drawRows(bodyEl, rows, columns, cellOf, '');
  }

  function sortRows(rows, valueOf, column, descending) {
    return rows.slice().sort(function (left, right) {
      var a = valueOf(left, column);
      var b = valueOf(right, column);
      var order = column.kind === 'text' ? String(a || '').localeCompare(String(b || '')) : a - b;
      return descending ? -order : order;
    });
  }

  function filterRows(rows, query, keys) {
    if (!query) { return rows.slice(); }
    var needle = query.toLowerCase();
    return rows.filter(function (row) {
      return keys.some(function (key) {
        return String(row[key] || '').toLowerCase().indexOf(needle) >= 0;
      });
    });
  }

  /** The [limit] largest rows by [valueOf], descending, so the first is the one a bar scales against. */
  function topBy(rows, valueOf, limit) {
    return rows.slice().sort(function (left, right) {
      return valueOf(right) - valueOf(left);
    }).slice(0, limit);
  }

  function drawBars(barsEl, items) {
    barsEl.textContent = '';
    var largest = items.length ? items[0].value : 0;
    items.forEach(function (item) {
      var row = document.createElement('div');
      row.className = 'bar-row';

      var name = document.createElement('div');
      name.className = 'bar-name';
      name.textContent = item.name;
      name.title = item.title || item.name;

      var track = document.createElement('div');
      track.className = 'bar-track';
      var bar = document.createElement('div');
      bar.className = 'bar';
      bar.style.width = (largest ? (item.value / largest) * 88 : 0) + '%';
      var label = document.createElement('span');
      label.className = 'bar-value';
      label.textContent = item.text;
      track.appendChild(bar);
      track.appendChild(label);

      row.appendChild(name);
      row.appendChild(track);
      barsEl.appendChild(row);
    });
  }

  return {
    duration: duration,
    share: share,
    bytes: bytes,
    talliesBytes: talliesBytes,
    formatCell: formatCell,
    counterCell: counterCell,
    highlight: highlight,
    drawTiles: drawTiles,
    drawList: drawList,
    drawHeader: drawHeader,
    drawRows: drawRows,
    drawTable: drawTable,
    sortRows: sortRows,
    filterRows: filterRows,
    topBy: topBy,
    drawBars: drawBars
  };
})();
